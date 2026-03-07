package com.prbotbenchmark.github;

public class GitHubAuthException extends RuntimeException {
    public GitHubAuthException(String message, Throwable cause) {
        super(message, cause);
    }
    public GitHubAuthException(String message) {
        super(message);
    }
}
