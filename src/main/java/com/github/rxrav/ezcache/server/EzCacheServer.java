package com.github.rxrav.ezcache.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rxrav.ezcache.core.Constants;
import com.github.rxrav.ezcache.core.Memory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class EzCacheServer {

    public static final int PORT = 6379;
    public static final String VN = "1.0";
    private final Memory memoryRef;
    private final Logger logger = LogManager.getLogger(EzCacheServer.class);
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final List<Socket> connectedClientList = new ArrayList<>();
    private boolean shouldAcceptConnections = true;

    public EzCacheServer() {
        logger.info("Building server memory...");
        this.memoryRef = new Memory();
        logger.info("Trying to restored db...");
        this.restoreDb();
        logger.info("Creating executor service...");
        ThreadFactory tf = Thread.ofVirtual().name("client-handler-", 1).factory();
        executorService = Executors.newFixedThreadPool(250, tf);
    }

    private void restoreDb() {
        try {
            File file = new File(Constants.DAT_FILE_NAME_AT_CURRENT_PATH);
            if (file.exists()) {
                logger.info("Backup file found...");
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String fileContentStr = new String(fileContent, StandardCharsets.UTF_8);

                String[] data = fileContentStr.split(Constants.SEPARATOR);
                ObjectMapper mapper = new ObjectMapper();
                this.memoryRef.setMainMemory(mapper.readValue(data[0], new TypeReference<>() {}));
                this.memoryRef.setExpiryMetadataRef(mapper.readValue(data[1], new TypeReference<>() {}));
                if (!this.memoryRef.getMainMemorySnapshot().isEmpty() ||
                        !this.memoryRef.getExpiryMetadataRefSnapshot().isEmpty()) {
                    logger.info("Data restored from backup file");
                }
            } else {
                logger.info("Backup file not found, nothing to restore");
            }
        } catch (IOException | RuntimeException e) {
            logger.error("Error while restoring: ", e);
            throw new RuntimeException(e);
        }
    }

    public void start() throws IOException {
        registerShutdownHook();
        this.serverSocket = new ServerSocket(PORT);
        logger.info("Server started, EzCache version {}", VN);
        try {
            logger.info("The EzCache server is now ready to accept connections on port {}", PORT);
            logger.info("Listening for connections...");
            while (shouldAcceptConnections) {
                Socket client = this.serverSocket.accept();
                this.connectedClientList.add(client);
                executorService.submit(() -> {
                    try {
                        new EzCacheConnHandler(client, this.memoryRef).handle();
                    } catch (IOException _) {}
                });
            }
        } catch (IOException e) {
            logger.info("Exited connection handler loop");
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Stopping EzCache server...");
            try {
                logger.info("Terminating client connections...");
                for(Socket connectedClient: this.connectedClientList) {
                    connectedClient.close();
                }
                logger.info("Shutting down executor service...");
                this.executorService.shutdown();
                this.stop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public void stop() throws IOException {
        shouldAcceptConnections = false;
        logger.info("Broke out of connection handler loop");
        serverSocket.close();
        logger.info("Connection closed. Bye.");
    }
}
