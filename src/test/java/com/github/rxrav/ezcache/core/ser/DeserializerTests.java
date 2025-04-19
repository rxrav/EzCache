package com.github.rxrav.ezcache.core.ser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.cert.CRL;

import static com.github.rxrav.ezcache.core.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DeserializerTests {

    static Resp2Deserializer deserializer;

    @BeforeAll
    static void beforeAll() {
        deserializer = new Resp2Deserializer();
    }

    @Test
    void shouldDeserializeSimpleString() {
        var msg = "+OK".concat(CRLF);
        var expected = "OK";
        String actual = deserializer.deserializeString(msg);
        assertEquals(expected, actual);
    }

    @Test
    void shouldDeserializeBulkString() {
        var msg = "$12"
                .concat(CRLF)
                .concat("hello")
                .concat(String.valueOf(CR))
                .concat("wo")
                .concat(String.valueOf(LF))
                .concat("rld")
                .concat(CRLF);
        var expected = "hello"
                .concat(String.valueOf(CR))
                .concat("wo")
                .concat(String.valueOf(LF))
                .concat("rld");
        String actual = deserializer.deserializeString(msg);
        assertEquals(expected, actual);
    }

    @Test
    void shouldDeserializeAnotherBulkString() {
        var msg = "$5".concat(CRLF).concat("hello").concat(CRLF);
        var expected = "hello";
        String actual = deserializer.deserializeString(msg);
        assertEquals(expected, actual);
    }

    @Test
    void shouldDeserializeNullString() {
        var actual = deserializer.deserializeString("$-1".concat(CRLF));
        assertNull(actual);
    }

    @Test
    void shouldDeserializeEmptyString() {
        var actual = deserializer.deserializeString("$0".concat(CRLF));
        assertEquals("", actual);
    }

    @Test
    void shouldDeserializeSimpleError() {
        var msg = "-test exception".concat(CRLF);
        var expected = new RuntimeException("test exception");
        var actual = deserializer.deserializeError(msg);
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @Test
    void shouldDeserializePositiveInteger() {
        var integer = ":99".concat(CRLF);
        var actual = deserializer.deserializeInteger(integer);
        assertEquals(99, actual);
    }

    @Test
    void shouldDeserializeNegativeInteger() {
        var integer = ":-99".concat(CRLF);
        var actual = deserializer.deserializeInteger(integer);
        assertEquals(-99, actual);
    }

    @Test
    void shouldDeserializeStrArray() {
        var msg = "*2"
                .concat(CRLF)
                .concat("$5")
                .concat(CRLF)
                .concat("hello")
                .concat(CRLF)
                .concat("$5")
                .concat(CRLF)
                .concat("world")
                .concat(CRLF);
        var expected = new String[] {"hello", "world"};
        var actual = deserializer.deserializeArray(msg);
        for(int i = 0; i < expected.length; i ++) {
            assertEquals(expected[i], actual[i].toString());
        }
    }

    @Test
    void shouldDeserializeIntArray() {
        var msg = "*2"
                .concat(CRLF)
                .concat(":1")
                .concat(CRLF)
                .concat(":2")
                .concat(CRLF);
        var expected = new int[] {1, 2};
        var actual = deserializer.deserializeArray(msg);
        for(int i = 0; i < expected.length; i ++) {
            assertEquals(expected[i], (int) actual[i]);
        }
    }

    @Test
    void shouldSerializeMixedArray() {
        var msg = "*2"
                .concat(CRLF)
                .concat("$5")
                .concat(CRLF)
                .concat("hello")
                .concat(CRLF)
                .concat(":2")
                .concat(CRLF);
        var actual = deserializer.deserializeArray(msg);
        assertEquals("hello", actual[0].toString());
        assertEquals(2, (int) actual[1]);
    }

    @Test
    void shouldDeserializeNullArray() {
        var actual = deserializer.deserializeArray("*-1".concat(CRLF));
        assertNull(actual);
    }

    @Test
    void shouldDeserializeEmptyArray() {
        var actual = deserializer.deserializeArray("*0".concat(CRLF));
        assertEquals(0, actual.length);
    }
}
