package com.sps.server.model;

import com.sps.server.ParseException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

/** not thread safe */
@RequiredArgsConstructor
public class Request {
    private static final int MAX_HEADER_SIZE = 8 * 1024;
    private static final int MAX_BODY_SIZE = 256 * 1024;
    private static final int READ_SOME_AMOUNT = 1024;
    private static final byte[] SPACE = " ".getBytes();
    private static final byte[] CR_LF = "\r\n".getBytes();
    private static final byte[] CR_LF_2 = "\r\n\r\n".getBytes();
    private static final List<Byte> REQUEST_LEADING_WHITESPACE = new LinkedList<>();

    static {
        REQUEST_LEADING_WHITESPACE.add((byte) ' ');
        REQUEST_LEADING_WHITESPACE.add((byte) '\r');
        REQUEST_LEADING_WHITESPACE.add((byte) '\n');
    }

    private final InputStream is;
    @Getter
    private final HttpMethod method;
    @Getter
    private final String url;
    @Getter
    private final String version;

    private ByteBuffer buf;
    private Map<String, String> headers;
    private byte[] body;

    private Request(InputStream is) throws IOException {
        this.is = is;
        this.buf = ByteBuffer.allocate(MAX_HEADER_SIZE);
        this.method = Enum.valueOf(HttpMethod.class, parseStringFollowedBy(SPACE));
        this.url = parseStringFollowedBy(SPACE);
        this.version = parseStringFollowedBy(CR_LF);
    }

    /**
     * Return empty if the input stream is an empty http request, otherwise return a request from
     * parsing the stream.
     *
     * This Request object will take ownership of the stream. No one else should use it.
     */
    public static Optional<Request> from(InputStream is) throws IOException {
        while (true) {
            int intB = is.read();
            if (intB == -1) {
                // EOF
                return Optional.empty();
            }
            byte b = (byte) intB;
            if (!REQUEST_LEADING_WHITESPACE.contains(b)) {
                InputStream sis =
                        new SequenceInputStream(new ByteArrayInputStream(new byte[]{b}), is);
                return Optional.of(new Request(sis));
            }
        }
    }

    public Map<String, String> readHeaders() throws IOException {
        if (headers == null) {
            // this doesn't handle double quotes or multiple headers with the same name according to rfc.
            int i = 0;
            String name = null;
            headers = new HashMap<>();
            while (i < CR_LF_2.length) {
                int read = is.read();
                if (read == -1) {
                    throw new ParseException(StatusCode.BAD_REQUEST, "EOF");
                }
                byte b = (byte) read;
                buf.put(b);
                if (b == CR_LF_2[i]) {
                    ++i;
                } else {
                    if (name == null) {
                        if (b == ':') {
                            name = readFromBuf(1);
                            buf.clear();
                        }
                    } else {
                        if (i == CR_LF.length) {
                            // header values wrapping over multiple lines must begin with SP or HT
                            if (b != ' ' && b != '\t') {
                                putHeader(headers, name, CR_LF.length + 1);
                                name = null;
                                buf.clear();
                                buf.put(b);
                            }
                        }
                    }
                    i = 0;
                }
            }
            if (name != null) {
                putHeader(headers, name, CR_LF_2.length);
            }
            buf.clear();
        }
        return headers;
    }

    public byte[] readBody() throws IOException {
        if (body == null) {
            readHeaders();
            String contentLength = headers.get("content-length");
            int length = Integer.parseInt(contentLength);
            if (length > MAX_BODY_SIZE) {
                throw new ParseException(StatusCode.ENTITY_TOO_LARGE, "Entity exceeded max size");
            }
            body = new byte[length];
            int bytesRead = read(body, length, is);
            if (bytesRead != length) {
                System.out.format("length: %d, read: %d%n", length, bytesRead);
                throw new ParseException(StatusCode.BAD_REQUEST, "content-length does not match entity size");
            }
        }
        return body;
    }

    /**
     * Read MIN(buffer.length, "length" bytes, remainingBytes(input)) into buffer.
     *
     * Return # bytes read.
     */
    private int read(byte [] buffer, long length, InputStream input) throws IOException {
        int totalBytesRead = 0;
        while (totalBytesRead < buffer.length && totalBytesRead < length) {
            int bytesRead = input.read(
                    buffer,
                    totalBytesRead,
                    (int) Math.min(Math.min(READ_SOME_AMOUNT, buffer.length - totalBytesRead), length - totalBytesRead));
            if (bytesRead == -1) {
                break;
            }
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    private String parseStringFollowedBy(byte[] following) throws IOException {
        int i = 0;
        while (i < following.length) {
            int read = is.read();
            if (read == -1) {
                throw new ParseException(StatusCode.BAD_REQUEST, "EOF");
            }
            byte b = (byte) read;
            buf.put(b);
            if (b == following[i]) {
                ++i;
            } else {
                i = 0;
            }
        }
        String str = readFromBuf(following.length);
        buf.clear();
        return str;
    }

    private void putHeader(Map<String, String> headers, String name, int minusOffset) {
        headers.put(name.toLowerCase().trim(), readFromBuf(minusOffset).trim());
    }

    private String readFromBuf(int minusOffset) {
        return new String(buf.array(), buf.arrayOffset(), buf.position() - minusOffset, Charset.forName("UTF-8"));
    }
}
