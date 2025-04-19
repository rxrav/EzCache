package com.github.rxrav.ezcache.core;

public class Constants {
    public static final char CR = '\r';
    public static final char LF = '\n';
    public static final String CRLF = String.valueOf(CR) + LF;

    // first bytes of different data types
    public static final char SIMPLE_STR = '+';
    public static final char SIMPLE_ERR = '-';
    public static final char INTEGER = ':';
    public static final char BULK_STR = '$';
    public static final char ARRAY = '*';

    // others
    public static final String SEPARATOR = "\n__SEP__\n";
    public static final String DAT_FILE_NAME_AT_CURRENT_PATH = "./ezcache.backup";

    // Will start showing error when 80% of memory allocated to JVM is full
    public static final float PERMITTED_MAIN_MEMORY_THRESHOLD = 0.8f;
    public static final int MAX_BUFFER_SIZE = 4096;
}
