package com.secureflow.identity.application;

public record RefreshOutcome(Status status, RefreshSessionResult result) {
    public enum Status {
        SUCCESS,
        INVALID,
        REPLAY
    }

    public static RefreshOutcome invalid() {
        return new RefreshOutcome(Status.INVALID, null);
    }

    public static RefreshOutcome replay() {
        return new RefreshOutcome(Status.REPLAY, null);
    }

    public static RefreshOutcome success(RefreshSessionResult result) {
        return new RefreshOutcome(Status.SUCCESS, result);
    }

    @Override
    public String toString() {
        return "RefreshOutcome[" + status + "]";
    }
}
