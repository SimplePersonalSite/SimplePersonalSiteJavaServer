package com.sps.server.model;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Response {
    private static final byte[] CR_LF = "\r\n".getBytes();
    private static final byte[] HTTP_VERSION = "HTTP/1.0 ".getBytes();
    private static final byte[] CONTENT_TYPE = "Content-Type: text/html; charset=UTF-8".getBytes();
    private static final byte[] CONTENT_LENGTH_PREFIX = "Content-Length: ".getBytes();
    private static final List<String> PROVIDED_HEADERS =
            Arrays.asList("content-type", "content-length");

    private final StatusCode status;
    private final byte[] body;
    private final Map<String, String> headers = new HashMap<>();

    public Response addHeader(String name, String value) {
        String lowerName = name.toLowerCase();
        if (!PROVIDED_HEADERS.contains(lowerName)) {
            headers.put(lowerName, value);
        }
        return this;
    }

    public void writeTo(OutputStream os) throws IOException {
        // status line
        os.write(HTTP_VERSION);
        os.write(("" + status.getCode()).getBytes());
        os.write((" " + status.toString()).getBytes());
        os.write(CR_LF);

        // headers
        os.write(CONTENT_TYPE);
        os.write(CR_LF);
        os.write(CONTENT_LENGTH_PREFIX);
        os.write(Integer.toString(body == null ? 0 : body.length).getBytes());
        os.write(CR_LF);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            os.write(header.getKey().getBytes());
            os.write(": ".getBytes());
            os.write(header.getValue().getBytes());
            os.write(CR_LF);
        }
        os.write(CR_LF);

        // body
        if (body != null) {
            os.write(body);
        }
    }
}
