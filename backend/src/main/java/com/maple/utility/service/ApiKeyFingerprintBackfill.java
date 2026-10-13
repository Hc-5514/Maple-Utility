package com.maple.utility.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Explicit idempotent maintenance command: --backfill-api-key-fingerprints (no key values in output). */
@Component
public class ApiKeyFingerprintBackfill implements ApplicationRunner {
    private final DataContextService contexts;
    public ApiKeyFingerprintBackfill(DataContextService contexts) { this.contexts = contexts; }
    @Override public void run(ApplicationArguments arguments) {
        if (arguments.containsOption("backfill-api-key-fingerprints")) contexts.backfillFingerprints();
    }
}
