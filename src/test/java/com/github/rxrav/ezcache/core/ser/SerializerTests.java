package com.github.rxrav.ezcache.core.ser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.github.rxrav.ezcache.core.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializerTests {

    static Resp2Serializer serializer;

    @BeforeAll
    static void beforeAll() {
        serializer = new Resp2Serializer();
    }

    @Test
    void shouldSerializeSimpleString() {
        var simpleStr = "hello world";
        var expected = "+hello world".concat(CRLF);
        var actual = serializer.serialize(simpleStr, false);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeBulkString() {
        var bulkStr = "hello"
                .concat(String.valueOf(CR))
                .concat("wo")
                .concat(String.valueOf(LF))
                .concat("rld");
        var expected = "$12"
                .concat(CRLF)
                .concat("hello")
                .concat(String.valueOf(CR))
                .concat("wo")
                .concat(String.valueOf(LF))
                .concat("rld")
                .concat(CRLF);
        var actual = serializer.serialize(bulkStr, true);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeAnotherBulkString() {
        var bulkStr = "hello";
        var expected = "$5".concat(CRLF).concat("hello").concat(CRLF);
        var actual = serializer.serialize(bulkStr, true);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeNullString() {
        var expected = "$-1".concat(CRLF);
        var actual = serializer.serialize((String) null, true);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeEmptyString() {
        var expected = "$0".concat(CRLF);
        var actual = serializer.serialize("", true);
        assertEquals(expected, actual);
    }

    @Test
    void shouldNotSerializeBulkStrAsSimpleStr() {
        var bulkStr = "hello"
                .concat(String.valueOf(CR))
                .concat("wo")
                .concat(String.valueOf(LF))
                .concat("rld");
        String expected = "-this is a bulk str, can't serialize as simple str".concat(CRLF);
        String actual = serializer.serialize(bulkStr, false);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeSimpleError() {
        var simpleError = new RuntimeException("test exception");
        var expected = "-test exception".concat(CRLF);
        var actual = serializer.serialize(simpleError);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializePositiveInteger() {
        var integer = 99;
        var expected = ":99".concat(CRLF);
        var actual = serializer.serialize(integer);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeNegativeInteger() {
        var integer = -99;
        var expected = ":-99".concat(CRLF);
        var actual = serializer.serialize(integer);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeStrArray() {
        var arr = new Object[] {"hello", "world"};
        var expected = "*2"
                .concat(CRLF)
                .concat("$5")
                .concat(CRLF)
                .concat("hello")
                .concat(CRLF)
                .concat("$5")
                .concat(CRLF)
                .concat("world")
                .concat(CRLF);
        var actual = serializer.serialize(arr);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeIntArray() {
        var arr = new Object[] {1, 2};
        var expected = "*2"
                .concat(CRLF)
                .concat(":1")
                .concat(CRLF)
                .concat(":2")
                .concat(CRLF);
        var actual = serializer.serialize(arr);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeMixedArray() {
        var arr = new Object[] {"hello", 2};
        var expected = "*2"
                .concat(CRLF)
                .concat("$5")
                .concat(CRLF)
                .concat("hello")
                .concat(CRLF)
                .concat(":2")
                .concat(CRLF);
        var actual = serializer.serialize(arr);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeNullArray() {
        var expected = "*-1".concat(CRLF);
        var actual = serializer.serialize((Object[]) null);
        assertEquals(expected, actual);
    }

    @Test
    void shouldSerializeEmptyArray() {
        var arr = new Object[] {};
        var expected = "*0".concat(CRLF);
        var actual = serializer.serialize(arr);
        assertEquals(expected, actual);
    }
}