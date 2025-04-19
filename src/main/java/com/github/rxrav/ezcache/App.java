package com.github.rxrav.ezcache;

import com.github.rxrav.ezcache.server.EzCacheServer;

import java.io.IOException;

public class App {
    public static void main( String[] args ) throws IOException {
        EzCacheServer server = new EzCacheServer();
        server.start();
    }
}
