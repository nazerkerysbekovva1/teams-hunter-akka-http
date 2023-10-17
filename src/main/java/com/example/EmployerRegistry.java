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

//#employer-registry-actor
public class EmployerRegistry extends AbstractBehavior<EmployerRegistry.Command> {

    // actor protocol
    interface Command {
    }

    public final static class GetEmployers implements Command {
        public final ActorRef<Employers> replyTo;

        public GetEmployers(ActorRef<Employers> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public final static class CreateEmployer implements Command {
        public final Employer employer;
        public final ActorRef<UserRegistry.ActionPerformed> replyTo;

        public CreateEmployer(Employer employer, ActorRef<UserRegistry.ActionPerformed> replyTo) {
            this.employer = employer;
            this.replyTo = replyTo;
        }
    }

    public final static class GetEmployerResponse {
        public final Optional<Employer> maybeEmployer;

        public GetEmployerResponse(Optional<Employer> maybeEmployer) {
            this.maybeEmployer = maybeEmployer;
        }
    }

    public final static class GetEmployer implements Command {
        public final String email;
        public final ActorRef<GetEmployerResponse> replyTo;

        public GetEmployer(String email, ActorRef<GetEmployerResponse> replyTo) {
            this.email = email;
            this.replyTo = replyTo;
        }
    }

    public final static class DeleteEmployer implements Command {
        public final String email;
        public final ActorRef<ActionPerformed> replyTo;

        public DeleteEmployer(String email, ActorRef<ActionPerformed> replyTo) {
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

    //#employer-case-classes
    public final static class Employer {
        public final String name;
        public final String email;
        public final String password;

        @JsonCreator
        public Employer(@JsonProperty("name") String name, @JsonProperty("email") String email, @JsonProperty("password") String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }

    public final static class Employers {
        public final List<Employer> employers;

        public Employers(List<Employer> employers) {
            this.employers = employers;
        }
    }
    //#employer-case-classes

    private final List<Employer> employers = new ArrayList<>();

    private EmployerRegistry(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(EmployerRegistry::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetEmployers.class, this::onGetEmployers)
                .onMessage(CreateEmployer.class, this::onCreateEmployer)
                .onMessage(GetEmployer.class, this::onGetEmployer)
                .onMessage(DeleteEmployer.class, this::onDeleteEmployer)
                .build();
    }

    private Behavior<Command> onGetEmployers(GetEmployers command) {
        // We must be careful not to send out employers since it is mutable
        // so for this response we need to make a defensive copy
        command.replyTo.tell(new Employers(Collections.unmodifiableList(new ArrayList<>(employers))));
        return this;
    }

    private Behavior<Command> onCreateEmployer(CreateEmployer command) {
        boolean employerExists = employers.stream()
                .anyMatch(employer -> employer.email.equals(command.employer.email));

        if (employerExists) {
            command.replyTo.tell(new UserRegistry.ActionPerformed(String.format("User with the same email already exists.")));
        } else {
            employers.add(command.employer);
            command.replyTo.tell(new UserRegistry.ActionPerformed(String.format("Employer %s created.", command.employer.email)));
        }
        return this;
    }

    private Behavior<Command> onGetEmployer(GetEmployer command) {
        Optional<Employer> maybeEmployer = employers.stream()
                .filter(employer -> employer.email.equals(command.email))
                .findFirst();
        command.replyTo.tell(new GetEmployerResponse(maybeEmployer));
        return this;
    }

    private Behavior<Command> onDeleteEmployer(DeleteEmployer command) {
        employers.removeIf(employer -> employer.email.equals(command.email));
        command.replyTo.tell(new ActionPerformed(String.format("Employer %s deleted.", command.email)));
        return this;
    }
}
//#employer-registry-actor
