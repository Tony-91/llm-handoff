package com.llmhandoff.error;

public class ManifestNotFoundException extends RuntimeException {
    public ManifestNotFoundException(String message) {
        super(message);
    }
}
