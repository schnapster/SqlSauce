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
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Properties;

/**
 * Created by napster on 08.10.17.
 *
 * Class initially heavily inspired by the way FredBoat handles the SSH tunnel (MIT license)
 */
public class SshTunnel {

    private static final Logger log = LoggerFactory.getLogger(SshTunnel.class);

    private final SshDetails sshDetails;
    private volatile Session sshTunnel;

    public SshTunnel(final SshDetails sshDetails) {
        this.sshDetails = sshDetails;
    }

    public synchronized SshTunnel connect() {

        if (this.sshTunnel != null && this.sshTunnel.isConnected()) {
            log.info("Tunnel is already connected, disconnect first before reconnecting");
            return this;
        }
        try {
            //establish the tunnel
            log.info("Starting SSH tunnel");

            final JSch jsch = new JSch();
            JSch.setLogger(new JSchLogger());

            final Session session = jsch.getSession(
                    this.sshDetails.user,
                    this.sshDetails.host,
                    this.sshDetails.sshPort
            );

            jsch.addIdentity(this.sshDetails.keyFile.getPath(), this.sshDetails.passphrase);

            final Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("ConnectionAttempts", "3");
            //dont set the keep alive too low or you might suffer from a breaking connection during start up (for whatever reason)
            session.setServerAliveInterval(1000);//milliseconds
            session.setConfig(config);

            //forward the port
            final int assignedPort = session.setPortForwardingL(
                    this.sshDetails.localPort,
                    "localhost",
                    this.sshDetails.remotePort
            );
            log.info("Port Forwarded: localhost:" + assignedPort + " -> " + this.sshDetails.host + ":" + this.sshDetails.remotePort);

            session.connect();
            log.info("SSH Connected");

            this.sshTunnel = session;
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

    public static class SshDetails {
        private String host;
        private String user;
        private File keyFile;              //private key file for auth
        private String passphrase = null;  //optional passphase for the keyfile
        private int sshPort = 22;          //port of the ssh service running on the remote machine
        private int localPort = 5432;      //the local endpoint of the tunnel; make sure it's available
        private int remotePort = 5432;     //port that the database is running on on the remote machine

        public SshDetails(@Nonnull final String host, @Nonnull final String user) {
            this.host = host;
            this.user = user;
            this.keyFile = new File("database-openssh.ppk");
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setHost(@Nonnull final String host) {
            this.host = host;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setUser(@Nonnull final String user) {
            this.user = user;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setKeyFile(@Nonnull final File file) {
            if (!file.exists()) {
                log.warn("Provided key file {} does not exist.", file.getPath());
            }
            this.keyFile = file;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setKeyFile(@Nonnull final String path) {
            return this.setKeyFile(new File(path));
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setPassphrase(@Nullable final String passphrase) {
            this.passphrase = passphrase;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setSshPort(final int sshPort) {
            this.sshPort = sshPort;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setLocalPort(final int localPort) {
            this.localPort = localPort;
            return this;
        }

        @Nonnull
        @CheckReturnValue
        public SshDetails setRemotePort(final int remotePort) {
            this.remotePort = remotePort;
            return this;
        }
    }
}
