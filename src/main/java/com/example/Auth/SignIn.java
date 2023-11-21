package com.example.Auth;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import com.example.User.UserRegistry;
import com.example.User.UserRoutes;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

class SignInRequest {
    public String email;
    public String password;

    @JsonCreator
    public SignInRequest(@JsonProperty("email") String email, @JsonProperty("password") String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    private String hashPassword(String password) {
        // You should configure BCrypt with proper cost factors and salt
        // For simplicity, we'll use default settings here.
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
}

public class SignIn {
//    private final static String SECRET_KEY = "Teams2024";
    private final static SecretKey SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final static Logger log = LoggerFactory.getLogger(UserRoutes.class);
    private final ActorRef<UserRegistry.Command> userRegistryActor;
    private final UserRegistry userRegistry; // Add a field for UserRegistry
    private final Duration askTimeout;
    private final Scheduler scheduler;

    public SignIn(
            ActorSystem<?> system,
            ActorRef<UserRegistry.Command> userRegistryActor,
            UserRegistry userRegistry) {
        this.userRegistryActor = userRegistryActor;
        this.userRegistry = userRegistry; // Initialize the userRegistry field
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    private CompletionStage<UserRegistry.ActionPerformed> signInUser(SignInRequest signInRequest, String userId) {
        return AskPattern.ask(
                userRegistryActor,
                ref -> new UserRegistry.SignIn(signInRequest.email, signInRequest.password, userId, ref),
                askTimeout,
                scheduler
        );
    }

    private String generateToken(String userId) {
        // Generate a JSON Web Token (JWT) with the userId as a claim
        return Jwts.builder()
                .claim("userId", userId)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }
    public Route signInRoutes() {
        return pathPrefix("sign-in", () ->
                concat(
                        pathEnd(() ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(SignInRequest.class),
                                                        signInRequest -> {
                                                            String userId = getUserUserId(signInRequest.email);
                                                            CompletionStage<UserRegistry.ActionPerformed> signInResult = signInUser(signInRequest, userId);
                                                            return onSuccess(signInResult, performed -> {
                                                                log.info("Sign-in result: {}", performed.description);
                                                                if (performed.description.equals("Sign-in successful")) {
                                                                    // Return a JSON response with userId and token
                                                                    return complete(StatusCodes.OK, new SignInResponse(generateToken(userId)), Jackson.marshaller());
                                                                } else {
                                                                    return complete(StatusCodes.UNAUTHORIZED, performed.description);
                                                                }
                                                            });
                                                        }
                                                )
                                        )
                                )
                        )
                )
        );
    }
    // Helper method to get the userId from the user's data
    private String getUserUserId(String email) {
        Optional<UserRegistry.User> maybeUser = userRegistry.getUsers().stream()
                .filter(u -> u.email.equals(email))
                .findFirst();
        if (maybeUser.isPresent()) {
            // Log the email and userId for debugging
            log.info("Found user for email {}: userId = {}", email, maybeUser.get().getUserId());
        } else {
            log.info("No user found for email: {}", email);
        }

        return maybeUser.map(UserRegistry.User::getUserId).orElse("");
    }
}
