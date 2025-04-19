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
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8))) {

            char[] incoming = new char[MAX_BUFFER_SIZE];
            int nosOfBytesRead;

            while((nosOfBytesRead = reader.read(incoming)) > 0) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < nosOfBytesRead; i ++) {
                    builder.append(incoming[i]);
                }
                logger.debug("Client sent: {}", builder.toString());
                Object cmdResp = new CommandHandler(
                        new Resp2Serializer(),
                        new Resp2Deserializer(),
                        memoryRef
                ).handleCommand(builder.toString());
                writer.write(cmdResp.toString());
                writer.flush();
            }
        } catch (SocketException e) {
            logger.info("Client disconnection requested");
            clientSocket.close();
        } catch (IOException e) {
            logger.error("RedisLite server unable to handle connection");
        }
    }
}
