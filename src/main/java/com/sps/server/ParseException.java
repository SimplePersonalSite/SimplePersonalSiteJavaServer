package com.sps.server;

import com.sps.server.model.StatusCode;
import lombok.Getter;

import java.io.IOException;

@Getter
public class ParseException extends IOException {
    private final StatusCode status;

    public ParseException(StatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public ParseException(StatusCode status, String message, Exception e) {
        super(message, e);
        this.status = status;
    }
}
