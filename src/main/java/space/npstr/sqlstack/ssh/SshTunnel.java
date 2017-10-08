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

package space.npstr.sqlstack.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.Properties;

/**
 * Created by napster on 08.10.17.
 */
public class SshTunnel {

    private static final Logger log = LoggerFactory.getLogger(SshTunnel.class);

    private final SshOptions sshOptions;
    private volatile Session sshTunnel;

    public SshTunnel(final SshOptions sshOptions) {
        this.sshOptions = sshOptions;
    }

    public synchronized SshTunnel connect() {

        if (this.sshTunnel != null && this.sshTunnel.isConnected()) {
            log.info("Tunnel is already connected, disconnect first before reconnecting");
            return this;
        }
        try {
            //establish the tunnel
            log.info("Starting SSH tunnel");

            final Properties config = new Properties();
            final JSch jsch = new JSch();
            JSch.setLogger(new JSchLogger());

            final Session session = jsch.getSession(this.sshOptions.user, this.sshOptions.host, this.sshOptions.sshPort);

            jsch.addIdentity(this.sshOptions.keyFile.getPath());
            config.put("StrictHostKeyChecking", "no");
            config.put("ConnectionAttempts", "3");
            session.setConfig(config);
            session.setServerAliveInterval(500);//milliseconds
            session.connect();

            log.info("SSH Connected");

            //forward the port
            final int assignedPort = session.setPortForwardingL(
                    this.sshOptions.localPort,
                    "localhost",
                    this.sshOptions.remotePort
            );

            this.sshTunnel = session;

            log.info("localhost:" + assignedPort + " -> " + this.sshOptions.host + ":" + this.sshOptions.remotePort);
            log.info("Port Forwarded");
        } catch (final Exception e) {
            throw new RuntimeException("Failed to start SSH tunnel", e);
        }
        return this;
    }

    public boolean isConnected() {
        return (this.sshTunnel != null && this.sshTunnel.isConnected());
    }

    public void disconnect() {
        this.sshTunnel.disconnect();
    }

    public static class SshOptions {
        private String host;
        private String user;
        private File keyFile;          //private key file for auth
        private int sshPort = 22;      //port of the ssh service running on the remote machine
        private int localPort = 1111;  //the local endpoint of the tunnel; make sure it's available
        private int remotePort = 5432; //port that the database is running on on the remote machine

        SshOptions(@Nonnull final String host, @Nonnull final String user) {
            this.host = host;
            this.user = user;
            this.keyFile = new File("database-openssh.ppk");
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setHost(@Nonnull final String host) {
            this.host = host;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setUser(@Nonnull final String user) {
            this.user = user;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setKeyFile(@Nonnull final File file) {
            if (!file.exists()) {
                log.warn("Set key file {} does not exist.", file.getPath());
            }
            this.keyFile = file;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setKeyFile(@Nonnull final String path) {
            return this.setKeyFile(new File(path));
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setSshPort(final int sshPort) {
            this.sshPort = sshPort;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setLocalPort(final int localPort) {
            this.localPort = localPort;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshOptions setRemotePort(final int remotePort) {
            this.remotePort = remotePort;
            return this;
        }
    }
}
