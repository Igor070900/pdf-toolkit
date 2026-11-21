package com.iedev.pdftoolkit.job.worker.exception;

public class RetryableProcessingException extends RuntimeException {

    public RetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}