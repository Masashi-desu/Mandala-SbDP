package io.github.mandala.sbdp.core;

public final class RefreshException extends RuntimeException {
    public RefreshException(String message) {
        super(message);
    }

    public RefreshException(String message, Throwable cause) {
        super(message, cause);
    }
}
