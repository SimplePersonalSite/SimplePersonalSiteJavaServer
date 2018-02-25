package com.sps.server;

import com.google.common.base.Preconditions;
import lombok.Builder;
import lombok.Setter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Builder
public class Server implements Runnable {
    private File rootFile;
    private int port;
    private int threads;

    private boolean sslEnabled;
    private String sslCertPassword;
    private InputStream keystoreInputStream;

    private boolean httpAuthEnabled;
    @Setter
    private String httpAuthUser;
    @Setter
    private String httpAuthPassword;

    private boolean running;

    @Override
    public void run() {
        if (httpAuthEnabled) {
            Preconditions.checkNotNull(httpAuthUser);
            Preconditions.checkNotNull(httpAuthPassword);
        }
        if (sslEnabled) {
            Preconditions.checkNotNull(sslCertPassword);
            Preconditions.checkNotNull(keystoreInputStream);
        }
        ServerSocket server = null;
        try {
            System.out.println("starting server");
            running = true;
            ExecutorService executorService = new ThreadPoolExecutor(
                    threads, threads, 1, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(threads));
            if (sslEnabled) {
                SSLContext sslContext = createSslContext();
                server = sslContext.getServerSocketFactory().createServerSocket(port);
            } else {
                server = new ServerSocket(port);
            }
            server.setSoTimeout(500);
            while (running) {
                Socket client = null;
                try {
                    client = server.accept();
                } catch (SocketTimeoutException e) {
                    // we set "so timeout" so we can periodically check our running flag and
                    // stop if we're no longer running.
                    continue;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    continue;
                }

                Runnable requestRunnable = new HandleRequestRunnable(client, httpAuthEnabled, httpAuthUser,
                        httpAuthPassword, rootFile);
                executorService.submit(requestRunnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            try {
                if (server != null && !server.isClosed()) {
                    server.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        running = false;
    }

    private SSLContext createSslContext() throws Exception{
        char[] password = sslCertPassword.toCharArray();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyStore ks = KeyStore.getInstance("BKS"); // make the keystore actually BKS

        ks.load(keystoreInputStream, password);
        keystoreInputStream.close();

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext;
    }
}