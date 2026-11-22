package com.iedev.pdftoolkit.job.worker.exception;

public class PermanentProcessingException extends RuntimeException {

    public PermanentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermanentProcessingException(String message) {
        super(message);
    }
}
