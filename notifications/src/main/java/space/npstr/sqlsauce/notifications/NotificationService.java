/*
 * MIT License
 *
 * Copyright (c) 2017-2018, Dennis Neufeld
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package space.npstr.sqlsauce.notifications;

import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.sqlsauce.DatabaseException;
import space.npstr.sqlsauce.notifications.exceptions.LoggingNsExceptionHandler;
import space.npstr.sqlsauce.notifications.exceptions.NsExceptionHandler;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by napster on 06.01.18.
 * <p>
 * This class supports Postgres' LISTEN / NOTIFY feature, mostly the LISTENs (NOTIFYs are easy, just shoot a query at the DB).
 * NOTIFY can be called on any database connection, but you can also use the built in method of this class.
 * <p>
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * ATTENTION: We can't use prepared statements for the queries issueing the LISTENs and UNLISTENs, so the channel names
 * need to be checked by the code calling the methods of these class for their legitimacy. Do not allow these to be set
 * by any user values ever or else sql injections might happen.
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * <p>
 * This class creates a single connection to the database. We cannot use a pooled connection, or integrate this feature
 * with a whole pool without a lot of hassle, as LISTENs and UNLISTENs would need to be executed on each connection all
 * the time. Given this and connections being a costly resource, you are encouraged, but not required, to use a single
 * object of this class in your application to register all your listeners with.
 * <p>
 * todo try async registering of listeners while listening to notifications
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    //todo is there a better way to synchronize / thread safety?
    //current thread safety design synchronizes on the listeners object whenever the Map or any of the Sets of its
    //values are accessed
    private final Map<String, Set<NotificationListener>> listeners = new HashMap<>();

    private final Connection connection;              //connection that we are listening on

    private final ExecutorService exec;                 //runs the main loop
    private final NsExceptionHandler exceptionHandler;    //handles all exceptions after successful init

    private volatile boolean pleaseShutdown = false;    // controls the main loop going on
    private volatile boolean hasShutdown = false;       // set to true once the main loop truly exits

    /**
     * @param jdbcUrl          Database that should be listened to.
     * @param name             A name for this object, will be used as part of the connection and thread names.
     * @param intervalMillis   Interval to sync listeners between listening for notifications. Value must be positive.
     *                         500ms is fine to use. This is the worst case delay that a listener may miss notifications
     *                         from after being registered.
     * @param exceptionHandler Optional exception handler. All thrown exceptions will be directed this way. If no handler
     *                         is provided (null value), exceptions will just be logged.
     * @throws DatabaseException Propagates any SQLExceptions while creating the connection
     */
    public NotificationService(String jdbcUrl, String name, int intervalMillis,
                               @Nullable NsExceptionHandler exceptionHandler) {
        Properties props = new Properties();
        props.setProperty("ApplicationName", name + "-" + NotificationService.class.getSimpleName());

        try {
            connection = DriverManager.getConnection(jdbcUrl, props);
            connection.unwrap(PgConnection.class);//test for the right underlying connection; spotbugs wont let us do this in a oneliner
        } catch (SQLException e) {
            throw new DatabaseException("Failed to create connection for NotificationService " + name, e);
        }

        if (exceptionHandler != null) {
            this.exceptionHandler = exceptionHandler;
        } else {
            this.exceptionHandler = new LoggingNsExceptionHandler(log);
        }

        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Interval needs to be a positive value.");
        }

        exec = Executors.newSingleThreadExecutor(
                r -> new Thread(r, NotificationService.class.getSimpleName() + "-executor-" + name)
        );

        exec.execute(() -> this.work(intervalMillis));
    }

    // ################################################################################
    // ##                           Listener management
    // ################################################################################

    /**
     * Add a listener to a channel.
     *
     * @param channel ATTENTION: We can't use prepared statements for the queries issueing the LISTENs and UNLISTENs,
     *                so the channel names need to be checked by the code calling this for their legitimacy.
     *                Do not allow these to be set by any user values ever or else sql injections might happen.
     */
    public NotificationService addListener(NotificationListener listener, String channel) {
        return addListener(listener, Collections.singleton(channel));
    }

    /**
     * Add a listener to a bunch of channels.
     *
     * @param channels ATTENTION: We can't use prepared statements for the queries issueing the LISTENs and UNLISTENs,
     *                 so the channel names need to be checked by the code calling this for their legitimacy.
     *                 Do not allow these to be set by any user values ever or else sql injections might happen.
     */
    public NotificationService addListener(NotificationListener listener, Collection<String> channels) {
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one channel to listen to");
        }
        synchronized (listeners) {
            for (String channel : channels) {
                if (channel.isEmpty()) {
                    throw new IllegalArgumentException("Channel may not be an empty string");
                }

                listeners.computeIfAbsent(channel, k -> new HashSet<>())
                        .add(listener);
            }
        }

        return this;
    }


    /**
     * Remove a listener from a channel.
     *
     * @param channel ATTENTION: We can't use prepared statements for the queries issueing the LISTENs and UNLISTENs,
     *                so the channel names need to be checked by the code calling this for their legitimacy.
     *                Do not allow these to be set by any user values ever or else sql injections might happen.
     */
    public NotificationService removeListener(NotificationListener listener, String channel) {
        return removeListener(listener, Collections.singleton(channel));
    }

    /**
     * Remove a listener from a bunch of channels.
     *
     * @param channels ATTENTION: We can't use prepared statements for the queries issueing the LISTENs and UNLISTENs,
     *                 so the channel names need to be checked by the code calling this for their legitimacy.
     *                 Do not allow these to be set by any user values ever or else sql injections might happen.
     */
    public NotificationService removeListener(NotificationListener listener, Collection<String> channels) {
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one channel to unlisten from");
        }
        synchronized (listeners) {
            for (String channel : channels) {
                if (channel.isEmpty()) {
                    throw new IllegalArgumentException("Channel may not be an empty string");
                }

                Set<NotificationListener> channelListeners = listeners.get(channel);
                if (channelListeners != null) channelListeners.remove(listener);
            }

            removeEmptyChannels();
        }

        return this;
    }


    /**
     * Remove a listener from all channels it is listening to.
     */
    public NotificationService removeListener(NotificationListener listener) {
        return removeListeners(Collections.singleton(listener));
    }

    /**
     * Remove a bunch of listeners from all channels they are listening to.
     */
    public NotificationService removeListeners(Collection<NotificationListener> listenersToRemove) {
        synchronized (listeners) {
            for (Set<NotificationListener> channel : listeners.values()) {
                channel.removeAll(listenersToRemove);
            }

            removeEmptyChannels();
        }

        return this;
    }

    //this method assumes that the caller is synchronizing on the listeners object
    private void removeEmptyChannels() {
        listeners.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    }

    // ################################################################################
    // ##                           Notifications
    // ################################################################################


    /**
     * Send a notification through this connection. You are probably better off using the other connection classes
     * {@link space.npstr.sqlsauce.DatabaseConnection} or {@link space.npstr.sqlsauce.DatabaseWrapper} to send
     * notifications, because this implementation will send notifications only between checking for new notifications,
     * and a heavy load of sending notifications through this class may delay the fetching of notifications.
     */
    public void notif(String channel, @Nullable String payload) {
        outstandingNotifs.add(new Notif(channel, payload));
    }

    // ################################################################################
    // ##                           Shutting down
    // ################################################################################

    /**
     * Tell this object to shut down soon:tm:. Returns immediately.
     * More information in {@link NotificationService#shutdownBlocking()}
     */
    public void shutdown() {
        pleaseShutdown = true;
    }

    /**
     * Blocks until the shutdown is done.
     * This will take at most the interval provided at creation time + the time to close and shut down the ressources used.
     * <p>
     * Absolutely not guarantees are in place with regards to what notifications will be sent or received once shutdown
     * has been initiated, although we will try to send out all pending notifications.
     */
    public void shutdownBlocking() {
        shutdown();
        while (!hasShutdown) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * @return Whether this NotificationService is considered to be shut down.
     * Registering listeners or attempting to send notifications when this returns true is futile.
     */
    public boolean isShutdown() {
        return pleaseShutdown;
    }


    // ################################################################################
    // ##                           Internals
    // ################################################################################

    //channels that we are actually listening to
    //between receiving notifications, these are updated with LISTEN and UNLISTEN to correctly represent the current
    // state of listeners
    private final Set<String> listening = new HashSet<>();

    //main work loop in here
    private void work(int intervalMillis) {
        while (!pleaseShutdown) {
            try {
                syncListeners();
                drainNotifs();

                PGNotification[] notifications = ((PgConnection) connection).getNotifications(intervalMillis);
                if (notifications != null) {
                    for (PGNotification notification : notifications) {
                        if (notification == null) {
                            continue; //better safe than sorry
                        }
                        synchronized (listeners) {
                            Set<NotificationListener> channelListeners = listeners.get(notification.getName());
                            if (channelListeners == null) {
                                continue;
                            }

                            for (NotificationListener listener : channelListeners) {
                                notifListener(listener, notification);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                exceptionHandler.handleNotificationServiceException(e);
            }
        }

        //if we reach this place, we need to shutdown our resources
        workLoopDone();
    }

    private void notifListener(NotificationListener listener, PGNotification notification) {
        try {
            listener.notif(notification);
        } catch (Exception e) {
            exceptionHandler.handleListenerException(e);
        }
    }

    private void workLoopDone() {
        // send out any outstanding notifications first though
        drainNotifs();
        // tell the database to unlisten
        try (Statement unlistenAll = connection.createStatement()) {
            //noinspection SqlResolve
            unlistenAll.executeUpdate("UNLISTEN *");
        } catch (Exception e) {
            exceptionHandler.handleNotificationServiceException(e);
        }
        //close the connection
        try {
            connection.close();
        } catch (Exception e) {
            exceptionHandler.handleNotificationServiceException(e);
        }
        //close the executor
        try {
            exec.shutdown();
        } catch (Exception e) {
            exceptionHandler.handleNotificationServiceException(e);
        }
        hasShutdown = true;
    }

    //notif type
    private static class Notif {
        public final String channel;
        @Nullable
        public final String payload;

        public Notif(String channel, @Nullable String payload) {
            this.channel = channel;
            this.payload = payload;
        }
    }

    private final Queue<Notif> outstandingNotifs = new ConcurrentLinkedQueue<>();

    //send out all notifications
    private void drainNotifs() {
        //noinspection SqlResolve
        try (PreparedStatement notify = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            Notif toSend = outstandingNotifs.poll();
            while (toSend != null) {
                notify.setString(1, toSend.channel);
                notify.setString(2, toSend.payload != null ? toSend.payload : "");
                notify.execute();
                toSend = outstandingNotifs.poll();
            }
        } catch (Exception e) {
            exceptionHandler.handleNotificationServiceException(e);
        }
    }

    //issue outstanding LISTEN / UNLISTEN statements
    private void syncListeners() {
        Set<String> toListen;
        Set<String> toUnlisten;
        synchronized (listeners) {
            Set<String> targetListeners = new HashSet<>(listeners.keySet());

            toListen = new HashSet<>(targetListeners);
            toListen.removeAll(listening);

            toUnlisten = new HashSet<>(listening);
            toUnlisten.removeAll(targetListeners);
        }

        for (String channel : toListen) {
            try (Statement listen = connection.createStatement()) {
                listen.executeUpdate("LISTEN " + channel + ";");
                listening.add(channel);
            } catch (Exception e) {
                exceptionHandler.handleNotificationServiceException(e);
            }
        }

        for (String channel : toUnlisten) {
            try (Statement unlisten = connection.createStatement()) {
                unlisten.executeUpdate("UNLISTEN " + channel + ";");
                listening.remove(channel);
            } catch (Exception e) {
                exceptionHandler.handleNotificationServiceException(e);
            }
        }
    }
}
