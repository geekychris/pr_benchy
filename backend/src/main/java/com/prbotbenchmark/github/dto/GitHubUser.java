package com.prbotbenchmark.github.dto;

public record GitHubUser(String login, @com.fasterxml.jackson.annotation.JsonProperty("avatar_url") String avatarUrl) {
}
