package com.secureflow.identity.application;

public final class ActiveAdministratorPolicy {
    private final AdministratorGuardPort guard;

    public ActiveAdministratorPolicy(AdministratorGuardPort guard) {
        this.guard = guard;
    }

    public void lock() {
        guard.lock();
    }

    public long count() {
        return guard.countActiveAdministrators();
    }

    public void requireAnotherActiveAdministrator() {
        guard.lock();
        if (guard.countActiveAdministrators() <= 1)
            throw new IllegalStateException("Cannot remove the last active administrator");
    }
}
