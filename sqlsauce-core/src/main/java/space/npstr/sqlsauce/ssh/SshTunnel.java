/*
 * MIT License
 *
 * Copyright (c) 2017 Dennis Neufeld
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

package space.npstr.sqlsauce.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.util.Properties;

/**
 * Created by napster on 08.10.17.
 * <p>
 * Class initially heavily inspired by the way FredBoat handles the SSH tunnel (MIT license)
 *
 * On connection loss, you want to call reconnect() as it creates a new session, because a session may not
 * always be recovered from a broken state by a disconnect() followed by a connect().
 */
@NotThreadSafe
public class SshTunnel {

    private static final Logger log = LoggerFactory.getLogger(SshTunnel.class);

    static {
        JSch.setLogger(new JSchLogger());
    }

    private final JSch jsch;

    private final SshDetails sshDetails;

    private Session currentSession;

    public SshTunnel(final SshDetails sshDetails) {
        this.jsch = new JSch();
        this.sshDetails = sshDetails;
        this.currentSession = configureSession();
    }

    public SshTunnel connect() {
        if (this.currentSession.isConnected()) {
            log.info("Tunnel is already connected, disconnect first before reconnecting");
            return this;
        }
        try {
            this.currentSession.connect();
            log.info("SSH Connected");
        } catch (final Exception e) {
            throw new RuntimeException("Failed to start SSH tunnel", e);
        }
        return this;
    }

    public boolean isConnected() {
        return this.currentSession.isConnected();
    }

    public SshTunnel reconnect() {
        disconnect();
        this.currentSession = configureSession();
        return connect();
    }

    public SshTunnel disconnect() {
        this.currentSession.disconnect();
        return this;
    }

    private Session configureSession() {
        try {
            //configure the tunnel
            log.info("Configuring SSH tunnel");
            Session session = jsch.getSession(
                    sshDetails.user,
                    sshDetails.host,
                    sshDetails.sshPort
            );

            jsch.addIdentity(sshDetails.keyFile.getPath(), sshDetails.passphrase);

            final Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("ConnectionAttempts", "3");
            //dont set the keep alive too low or you might suffer from a breaking connection during start up (for whatever reason)
            session.setServerAliveInterval(1000);//milliseconds
            session.setConfig(config);

            //forward the port
            final int assignedPort = session.setPortForwardingL(
                    sshDetails.localPort,
                    "localhost",
                    sshDetails.remotePort
            );
            log.info("Setting up port forwarding: localhost:" + assignedPort + " -> " + sshDetails.host + ":" + sshDetails.remotePort);

            return session;
        } catch (final JSchException e) {
            throw new RuntimeException("Failed to configure SSH tunnel", e);
        }
    }

    public static class SshDetails {
        private String host;
        private String user;
        private File keyFile;              //private key file for auth
        private String passphrase = null;  //optional passphase for the keyfile
        private int sshPort = 22;          //port of the ssh service running on the remote machine
        private int localPort = 5432;      //the local endpoint of the tunnel; make sure it's available
        private int remotePort = 5432;     //port that the database is running on on the remote machine

        public SshDetails(final String host, final String user) {
            this.host = host;
            this.user = user;
            this.keyFile = new File("database-openssh.ppk");
        }

        @CheckReturnValue
        public SshDetails setHost(final String host) {
            this.host = host;
            return this;
        }

        @CheckReturnValue
        public SshDetails setUser(final String user) {
            this.user = user;
            return this;
        }

        @CheckReturnValue
        public SshDetails setKeyFile(final File file) {
            if (!file.exists()) {
                log.warn("Provided key file {} does not exist.", file.getPath());
            }
            this.keyFile = file;
            return this;
        }

        @CheckReturnValue
        public SshDetails setKeyFile(final String path) {
            return this.setKeyFile(new File(path));
        }

        @CheckReturnValue
        public SshDetails setPassphrase(@Nullable final String passphrase) {
            this.passphrase = passphrase;
            return this;
        }

        @CheckReturnValue
        public SshDetails setSshPort(final int sshPort) {
            this.sshPort = sshPort;
            return this;
        }

        @CheckReturnValue
        public SshDetails setLocalPort(final int localPort) {
            this.localPort = localPort;
            return this;
        }

        @CheckReturnValue
        public SshDetails setRemotePort(final int remotePort) {
            this.remotePort = remotePort;
            return this;
        }
    }
}
