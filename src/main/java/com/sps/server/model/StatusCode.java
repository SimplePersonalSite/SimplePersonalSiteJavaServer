package com.sps.server.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum StatusCode {
    OK(200),
    BAD_REQUEST(400),
    INTERNAL_ERROR(500),
    NOT_FOUND(404),
    UNAUTHORIZED(401),
    ENTITY_TOO_LARGE(413);

    private int code;

    public int getCode() {
        return code;
    }
}