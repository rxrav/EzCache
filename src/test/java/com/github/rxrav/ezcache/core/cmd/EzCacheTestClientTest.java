package com.github.rxrav.ezcache.core.cmd;

import com.github.rxrav.ezcache.AppTest;
import com.github.rxrav.ezcache.client.EzCacheTestClient;
import com.github.rxrav.ezcache.core.ser.Resp2Deserializer;
import com.github.rxrav.ezcache.core.ser.Resp2Serializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EzCacheTestClientTest {

    @BeforeAll
    static void _startServer() {
        AppTest.startServer();
    }

    @AfterAll
    static void _stopServer() throws IOException {
        AppTest.stopServer();
    }

    @Test
    void shouldPing() throws InterruptedException {
        EzCacheTestClient client = new EzCacheTestClient("alpha");
        client.tryConnect();
        String response1 = client.send(new Resp2Serializer().serialize(new String[]{"ping"}));
        assertEquals("PONG", new Resp2Deserializer().deserializeString(response1));
        client.disconnect();
    }

    @Test
    void shouldEcho() throws InterruptedException {
        EzCacheTestClient client = new EzCacheTestClient("delta");
        client.tryConnect();
        String response3 = client.send(new Resp2Serializer().serialize(new String[]{"echo", "\"hello world\""}));
        assertEquals("\"hello world\"", new Resp2Deserializer().deserializeString(response3));
        client.disconnect();
    }
}
