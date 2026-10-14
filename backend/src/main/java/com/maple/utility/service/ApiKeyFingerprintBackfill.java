package com.maple.utility.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Explicit idempotent maintenance command: --backfill-api-key-fingerprints (no key values in output). */
@Component
public class ApiKeyFingerprintBackfill implements ApplicationRunner {
    private final DataContextService contexts;
    private final com.maple.utility.repository.UserApiKeyRepository keys;
    public ApiKeyFingerprintBackfill(DataContextService contexts, com.maple.utility.repository.UserApiKeyRepository keys) {
        this.contexts = contexts; this.keys = keys;
    }
    @Override public void run(ApplicationArguments arguments) {
        if (arguments.containsOption("backfill-api-key-fingerprints")) contexts.backfillFingerprints();
        if (arguments.containsOption("reconcile-api-key-data-contexts")) {
            for (Long id : keys.findAll().stream().map(com.maple.utility.entity.UserApiKey::getId).toList()) {
                try { contexts.reconcileCredential(id); }
                catch (com.maple.utility.exception.ApiException exception) {
                    org.slf4j.LoggerFactory.getLogger(ApiKeyFingerprintBackfill.class)
                            .warn("Credential reconciliation deferred: credentialId={}, code={}", id, exception.getCode());
                }
            }
        }
    }
}
