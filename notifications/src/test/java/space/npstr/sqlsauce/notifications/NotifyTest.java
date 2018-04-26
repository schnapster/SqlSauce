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

import org.junit.jupiter.api.Test;
import space.npstr.sqlsauce.BaseTest;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.notifications.exceptions.SimpleNsExceptionHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by napster on 20.01.18.
 */
class NotifyTest extends BaseTest {

    @Test
    public void pgNotifyViaNotificationService() throws InterruptedException {
        int interval = 100;
        String channel = "foo";
        String payload = "bar";

        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        NotificationService ns = new NotificationService(getTestJdbcUrl(), NotifyTest.class.getSimpleName(),
                interval, (SimpleNsExceptionHandler) exceptions::add);

        AtomicInteger notifications = new AtomicInteger(0);
        AtomicBoolean isBar = new AtomicBoolean(false);
        ns.addListener(notification -> {
            notifications.getAndIncrement();
            isBar.set(notification.getParameter().equals(payload));
        }, channel);

        Thread.sleep(interval * 2);
        ns.notif(channel, payload);
        Thread.sleep(interval * 2);
        ns.shutdown();

        for (Exception e : exceptions) {
            log.error("NotificationService threw exception", e);
        }
        assertEquals(0, exceptions.size(), "NotificationService threw exceptions");
        assertEquals(1, notifications.get(), "Did not receive notification");
        assertTrue(isBar.get(), "Payload was wrong");
    }

    @Test
    public void pgNotifyViaDatabaseWrapper() throws InterruptedException {
        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());

        int interval = 100;
        String channel = "foo";
        String payload = "bar";

        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        NotificationService ns = new NotificationService(getTestJdbcUrl(), NotifyTest.class.getSimpleName(),
                interval, (SimpleNsExceptionHandler) exceptions::add);

        AtomicInteger notifications = new AtomicInteger(0);
        AtomicBoolean isBar = new AtomicBoolean(false);
        ns.addListener(notification -> {
            notifications.getAndIncrement();
            isBar.set(notification.getParameter().equals(payload));
        }, channel);

        Thread.sleep(interval * 2);
        wrapper.notif(channel, payload);
        Thread.sleep(interval * 2);
        ns.shutdown();

        for (Exception e : exceptions) {
            log.error("NotificationService threw exception", e);
        }
        assertEquals(0, exceptions.size(), "NotificationService threw exceptions");
        assertEquals(1, notifications.get(), "Did not receive notification");
        assertTrue(isBar.get(), "Payload was wrong");
    }
}
