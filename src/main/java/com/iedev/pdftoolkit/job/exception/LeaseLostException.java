package com.iedev.pdftoolkit.job.exception;

import java.util.UUID;

public class LeaseLostException extends RuntimeException {

    public LeaseLostException(UUID jobId) {
        super("Job lease lost: " + jobId);
    }

    public LeaseLostException(String message) {
        super(message);
    }
}