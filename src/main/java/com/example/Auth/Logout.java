package com.example.Auth;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class Logout {
    private final static Logger log = LoggerFactory.getLogger(Logout.class);

    private Map<String, Boolean> tokenValidityMap = new ConcurrentHashMap<>();

    private CompletionStage<String> logoutUser(String token) {
        if (token == null || token.trim().isEmpty()) {
            return CompletableFuture.completedFuture("Invalid token. Please provide a valid token.");
        }
        tokenValidityMap.put(token, false);
        log.info("User with token {} logged out.", token);
        return CompletableFuture.completedFuture("Logout successful");
    }

    public Route logoutRoutes() {
        return pathPrefix("logout", () ->
                concat(
                        post(() ->
                                entity(
                                        Jackson.unmarshaller(TokenRequest.class),
                                        tokenRequest ->
                                                onSuccess(logoutUser(tokenRequest.getToken()), result ->
                                                        complete(StatusCodes.OK, result, Jackson.marshaller())
                                                )
                                )
                        )
                )
        );
    }

    public static class TokenRequest {
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
