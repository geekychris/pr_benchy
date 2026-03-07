package com.prbotbenchmark.runner.domain;

public enum ReplayStatus {
    PENDING,
    CREATING_BRANCHES,
    WAITING_FOR_BOTS,
    COLLECTING_COMMENTS,
    COMPLETED,
    FAILED
}
