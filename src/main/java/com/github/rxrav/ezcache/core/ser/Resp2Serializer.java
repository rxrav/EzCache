package com.github.rxrav.ezcache.core.ser;

import static com.github.rxrav.ezcache.core.Constants.*;

public class Resp2Serializer {

    public String serialize(String str, boolean isBulk) {
        if (isBulk) {
            if (str == null) {
                return String.valueOf(BULK_STR).concat("-1").concat(CRLF);
            } else if (str.isEmpty()) {
                return String.valueOf(BULK_STR).concat("0").concat(CRLF);
            } else {
                return String.valueOf(BULK_STR)
                        .concat(String.valueOf(str.length()))
                        .concat(CRLF)
                        .concat(str)
                        .concat(CRLF);
            }
        } else {
            return (str.indexOf(CR) > 0 || str.indexOf(LF) > 0) ?
            serialize(new RuntimeException("this is a bulk str, can't serialize as simple str")) :
            String.valueOf(SIMPLE_STR)
                    .concat(str)
                    .concat(CRLF);
        }
    }

    public String serialize(RuntimeException error) {
        return String.valueOf(SIMPLE_ERR)
                .concat(error.getMessage())
                .concat(CRLF);
    }

    public String serialize(int integer) {
        return String.valueOf(INTEGER)
                .concat(String.valueOf(integer))
                .concat(CRLF);
    }

    public String serialize(Object[] arr) {
        String des = String.valueOf(ARRAY);
        if (arr == null) {
            des = des.concat("-1").concat(CRLF);
        } else if (arr.length == 0) {
            des = des.concat("0").concat(CRLF);
        } else {
            des = des.concat(String.valueOf(arr.length)).concat(CRLF);
            for (Object obj : arr) {
                if (obj instanceof String) {
                    des = des.concat(serialize((String) obj, true));
                } else if (obj instanceof Integer) {
                    des = des.concat(serialize((Integer) obj));
                }
            }
        }
        return des;
    }
}
