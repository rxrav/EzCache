package com.github.rxrav.ezcache.server;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.cmd.CommandHandler;
import com.github.rxrav.ezcache.core.ser.Resp2Deserializer;
import com.github.rxrav.ezcache.core.ser.Resp2Serializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static com.github.rxrav.ezcache.core.Constants.MAX_BUFFER_SIZE;

public class EzCacheConnHandler {

    private final Logger logger = LogManager.getLogger(EzCacheConnHandler.class);
    private final Socket clientSocket;
    private final Memory memoryRef;

    public EzCacheConnHandler(Socket clientSocket, Memory memoryRef) {
        this.clientSocket = clientSocket;
        this.memoryRef = memoryRef;
    }

    public void handle() throws IOException {
        logger.debug("Client connected!");
        // Unbounded queue: reader never blocks on put(), writer drains in order.
        // Optional.empty() is the drain sentinel — a type-safe alternative to a magic string.
        LinkedBlockingQueue<Optional<String>> responses = new LinkedBlockingQueue<>();
        CountDownLatch writerDone = new CountDownLatch(1);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8))) {

            // Writer virtual thread: drains the queue and flushes each response independently,
            // so the reader can keep processing the next command without waiting for I/O.
            Thread.ofVirtual().name("conn-writer").start(() -> {
                try {
                    Optional<String> item;
                    while ((item = responses.take()).isPresent()) {
                        writer.write(item.get());
                        writer.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    logger.error("Write error on client {}: {}", clientSocket.getRemoteSocketAddress(), e.getMessage());
                } finally {
                    writerDone.countDown();
                }
            });

            // Reader loop (current thread): parse commands and enqueue responses.
            char[] incoming = new char[MAX_BUFFER_SIZE];
            int nosOfBytesRead;

            try {
                while ((nosOfBytesRead = reader.read(incoming)) > 0) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < nosOfBytesRead; i++) {
                        builder.append(incoming[i]);
                    }
                    logger.debug("Client sent: {}", builder);
                    String cmdResp = new CommandHandler(
                            new Resp2Serializer(),
                            new Resp2Deserializer(),
                            memoryRef
                    ).handleCommand(builder.toString());
                    responses.put(Optional.of(cmdResp));
                }
            } catch (SocketException e) {
                logger.info("Client disconnection requested");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Signal writer that no more responses are coming, then wait for it to flush.
            responses.put(Optional.empty());
            writerDone.await();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.error("RedisLite server unable to handle connection");
        } finally {
            clientSocket.close();
        }
    }
}

