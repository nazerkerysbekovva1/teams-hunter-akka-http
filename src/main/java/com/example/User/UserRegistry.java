package com.example.User;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mindrot.jbcrypt.BCrypt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class UserRegistry extends AbstractBehavior<UserRegistry.Command> {
    // Actor protocol
    public interface Command {}

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
            this.user = new User(
                    user.getUserId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPassword(),
                    user.getRole()
            );
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

    public final static class SignIn implements Command {
        public final String email;
        public final String password;
        public final ActorRef<ActionPerformed> replyTo;
        public final String userId;

        @JsonCreator
        public SignIn(
                @JsonProperty("email") String email,
                @JsonProperty("password") String password,
                @JsonProperty("userId") String userId,
                @JsonProperty("replyTo") ActorRef<ActionPerformed> replyTo) {
            this.email = email;
            this.password = password;
            this.replyTo = replyTo;
            this.userId = userId;
        }

        private String hashPassword(String password) {
            // You should configure BCrypt with proper cost factors and salt
            // For simplicity, we'll use default settings here.
            return BCrypt.hashpw(password, BCrypt.gensalt());
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
        public final String userId;
        public final String name;
        public final String email;
        public final String password;
        public final String role; // Add role for jobseeker or employer

        @JsonCreator
        public User(
                @JsonProperty("id") String userId,
                @JsonProperty("name") String name,
                @JsonProperty("email") String email,
                @JsonProperty("password") String password,
                @JsonProperty("role") String role
        ) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.password = hashPassword(password);
            System.out.println("this.password hash: " + hashPassword(password));
            System.out.println("userId: " + userId);;
            System.out.println("toString: " + this.toString());
            this.role = role;
        }
        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", password='" + password + '\'' +
                    ", role='" + role + '\'' +
                    ", userId='" + userId + '\'' +
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

        public String getUserId() { return userId; }


        // Hash the password using BCrypt
        private String hashPassword(String password) {
            if (isHashedPassword(password)) {
                return password;  // Already hashed
            } else {
                return BCrypt.hashpw(password, BCrypt.gensalt());
            }
        }

        // Verify a plain text password against the hashed password
        public boolean verifyPassword(String plainTextPassword) {
            System.out.println("plainTextPassword: " + plainTextPassword);
            System.out.println("this.password: " + this.password);
            System.out.println("verifyPassword: " + BCrypt.checkpw(plainTextPassword, this.password));
            return BCrypt.checkpw(plainTextPassword, this.password);
        }

        // Method to check if a password is hashed
        private boolean isHashedPassword(String password) {
            // This is a basic check; you might need to improve it based on your specific requirements
            return password.startsWith("$2a$");
        }
    }

    public final static class Users {
        public final List<User> users;

        public Users(List<User> users) {
            this.users = users;
        }
    }

    private final List<User> users = new ArrayList<>();

    public List<User> getUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users));
    }

    public UserRegistry(ActorContext<Command> context) {
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
                .onMessage(SignIn.class, this::onSignIn)
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
            String userId = UUID.randomUUID().toString(); // Generate the hashed user ID
            User userWithUserId = new User(userId, command.user.name, command.user.email, command.user.password, command.user.role);
            users.add(userWithUserId);
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

    private Behavior<Command> onSignIn(SignIn command) {
        Optional<User> maybeUser = users.stream()
                .filter(user -> user.email.equals(command.email))
                .findFirst();

        if (maybeUser.isPresent() && maybeUser.get().verifyPassword(command.password)) {
            command.replyTo.tell(new ActionPerformed("Sign-in successful"));
        } else {
            command.replyTo.tell(new ActionPerformed("Sign-in failed. Invalid email or password"));
        }

        return this;
    }

}
