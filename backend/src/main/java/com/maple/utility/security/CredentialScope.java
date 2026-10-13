package com.maple.utility.security;

/** Request/job-scoped credential ID. A missing selected credential never falls back to another key. */
public final class CredentialScope implements AutoCloseable {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private final Long previous;
    private CredentialScope(Long credentialId) {
        previous = CURRENT.get();
        if (credentialId == null) CURRENT.remove(); else CURRENT.set(credentialId);
    }
    public static CredentialScope use(Long credentialId) { return new CredentialScope(credentialId); }
    public static Long current() { return CURRENT.get(); }
    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
