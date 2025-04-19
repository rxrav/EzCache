package com.github.rxrav.ezcache;

import com.github.rxrav.ezcache.client.EzCacheTestClient;
import com.github.rxrav.ezcache.server.EzCacheServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class AppTest {
    public static EzCacheServer server;
    public static void startServer() {
        server = new EzCacheServer();
        Runnable runnable = () -> {
            try {
                server.start();
            } catch (RuntimeException | IOException e) {
                throw new RuntimeException(e);
            }
        };
        Thread serverThread = new Thread(runnable);
        serverThread.start();
    }

    public static void stopServer() throws IOException {
        server.stop();
    }

    @BeforeAll
    static void _startServer() {
        startServer();
    }

    @AfterAll
    static void _stopServer() throws IOException {
        stopServer();
    }

    @Test
    void shouldConnectAndDisconnect() throws InterruptedException {
        EzCacheTestClient client = new EzCacheTestClient("beta");
        client.tryConnect();
        client.disconnect();
    }
}