package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class UserRegistry extends AbstractBehavior<UserRegistry.Command> {
    // Actor protocol
    interface Command {}

    public final static class GetUsers implements Command {
        public final ActorRef<Users> replyTo;

        public GetUsers(ActorRef<Users> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public final static class CreateUser implements Command {
        public final User user;
        public final ActorRef<ActionPerformed> replyTo;

        public CreateUser(User user, ActorRef<ActionPerformed> replyTo) {
            this.user = user;
            this.replyTo = replyTo;
        }
    }

    public final static class GetUserResponse {
        public final Optional<User> maybeUser;

        public GetUserResponse(Optional<User> maybeUser) {
            this.maybeUser = maybeUser;
        }

        @Override
        public String toString() {
            if (maybeUser.isPresent()) {
                return "GetUserResponse: User found - " + maybeUser.get();
            } else {
                return "GetUserResponse: User not found";
            }
        }
    }

    public final static class GetUser implements Command {
        public final String email;
        public final ActorRef<GetUserResponse> replyTo;

        public GetUser(String email, ActorRef<GetUserResponse> replyTo) {
            this.email = email;
            this.replyTo = replyTo;
        }
    }

    public final static class DeleteUser implements Command {
        public final String email;
        public final ActorRef<ActionPerformed> replyTo;

        public DeleteUser(String email, ActorRef<ActionPerformed> replyTo) {
            this.email = email;
            this.replyTo = replyTo;
        }
    }

    public final static class ActionPerformed implements Command {
        public final String description;

        public ActionPerformed(String description) {
            this.description = description;
        }
    }

    public List<User> getUsersList() {
        return Collections.unmodifiableList(new ArrayList<>(users));
    }

    public final static class User {
        public final String name;
        public final String email;
        public final String password;
        public final String role; // Add role for jobseeker or employer

        @JsonCreator
        public User(
                @JsonProperty("name") String name,
                @JsonProperty("email") String email,
                @JsonProperty("password") String password,
                @JsonProperty("role") String role
        ) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
        }
        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", password='" + password + '\'' +
                    ", role='" + role + '\'' +
                    '}';
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }

        public String getRole() {
            return role;
        }
    }

    public final static class Users {
        public final List<User> users;

        public Users(List<User> users) {
            this.users = users;
        }
    }

    private final List<User> users = new ArrayList<>();

    private UserRegistry(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(UserRegistry::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetUsers.class, this::onGetUsers)
                .onMessage(CreateUser.class, this::onCreateUser)
                .onMessage(GetUser.class, this::onGetUser)
                .onMessage(DeleteUser.class, this::onDeleteUser)
                .build();
    }

    private Behavior<Command> onGetUsers(GetUsers command) {
        // We must be careful not to send out users since it is mutable
        // so for this response, we need to make a defensive copy
        command.replyTo.tell(new Users(Collections.unmodifiableList(new ArrayList<>(users))));
        return this;
    }

    private Behavior<Command> onCreateUser(CreateUser command) {
        boolean userExists = users.stream()
                .anyMatch(user -> user.email.equals(command.user.email));

        if (userExists) {
            command.replyTo.tell(new ActionPerformed(String.format("User with the same email already exists.")));
        } else {
            users.add(command.user);
            command.replyTo.tell(new ActionPerformed(String.format("User %s created.", command.user.email)));
        }
        return this;
    }

    private Behavior<Command> onGetUser(GetUser command) {
        Optional<User> maybeUser = users.stream()
                .filter(user -> user.email.equals(command.email))
                .findFirst();
        command.replyTo.tell(new GetUserResponse(maybeUser));
        return this;
    }

    private Behavior<Command> onDeleteUser(DeleteUser command) {
        users.removeIf(user -> user.email.equals(command.email));
        command.replyTo.tell(new ActionPerformed(String.format("User %s deleted.", command.email)));
        return this;
    }
}
