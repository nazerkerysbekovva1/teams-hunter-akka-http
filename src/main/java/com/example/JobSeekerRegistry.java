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

//#jobseeker-registry-actor
public class JobSeekerRegistry extends AbstractBehavior<JobSeekerRegistry.Command>  {

  // actor protocol
  interface Command {}

  public final static class GetJobSeekers implements Command {
    public final ActorRef<JobSeekers> replyTo;
    public GetJobSeekers(ActorRef<JobSeekers> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public final static class CreateJobSeeker implements Command {
    public final JobSeeker jobSeeker;
    public final ActorRef<UserRegistry.ActionPerformed> replyTo;
    public CreateJobSeeker(JobSeeker jobSeeker, ActorRef<UserRegistry.ActionPerformed> replyTo) {
      this.jobSeeker = jobSeeker;
      this.replyTo = replyTo;
    }

  }

  public final static class GetJobSeekerResponse {
    public final Optional<JobSeeker> maybeJobSeeker;
    public GetJobSeekerResponse(Optional<JobSeeker> maybeJobSeeker) {
      this.maybeJobSeeker = maybeJobSeeker;
    }
  }

  public final static class GetJobSeeker implements Command {
    public final String email;
    public final ActorRef<GetJobSeekerResponse> replyTo;
    public GetJobSeeker(String email, ActorRef<GetJobSeekerResponse> replyTo) {
      this.email = email;
      this.replyTo = replyTo;
    }
  }

  public final static class DeleteJobSeeker implements Command {
    public final String email;
    public final ActorRef<ActionPerformed> replyTo;
    public DeleteJobSeeker(String email, ActorRef<ActionPerformed> replyTo) {
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

  //#jobseeker-case-classes
  public final static class JobSeeker {
    public final String name;
    public final String email;
    public final String password;
    @JsonCreator
    public JobSeeker(@JsonProperty("name") String name, @JsonProperty("email") String email, @JsonProperty("password") String password) {
      this.name = name;
      this.email = email;
      this.password = password;
    }
  }

  public final static class JobSeekers {
    public final List<JobSeeker> jobSeekers;
    public JobSeekers(List<JobSeeker> jobSeekers) {
      this.jobSeekers = jobSeekers;
    }
  }
  //#jobseeker-case-classes

  private final List<JobSeeker> jobSeekers = new ArrayList<>();

  private JobSeekerRegistry(ActorContext<Command> context) {
    super(context);
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(JobSeekerRegistry::new);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
            .onMessage(GetJobSeekers.class, this::onGetJobSeekers)
            .onMessage(CreateJobSeeker.class, this::onCreateJobSeeker)
            .onMessage(GetJobSeeker.class, this::onGetJobSeeker)
            .onMessage(DeleteJobSeeker.class, this::onDeleteJobSeeker)
            .build();
  }

  private Behavior<Command> onGetJobSeekers(GetJobSeekers command) {
    // We must be careful not to send out jobSeekers since it is mutable
    // so for this response we need to make a defensive copy
    command.replyTo.tell(new JobSeekers(Collections.unmodifiableList(new ArrayList<>(jobSeekers))));
    return this;
  }

  private Behavior<Command> onCreateJobSeeker(CreateJobSeeker command) {
    boolean jobSeekerExists = jobSeekers.stream()
            .anyMatch(jobSeeker -> jobSeeker.email.equals(command.jobSeeker.email));

    if (jobSeekerExists) {
      command.replyTo.tell(new UserRegistry.ActionPerformed(String.format("User with the same email already exists.")));
    } else {
      jobSeekers.add(command.jobSeeker);
      command.replyTo.tell(new UserRegistry.ActionPerformed(String.format("JobSeeker %s created.", command.jobSeeker.name)));
    }
    return this;
  }

  private Behavior<Command> onGetJobSeeker(GetJobSeeker command) {
    Optional<JobSeeker> maybeJobSeeker = jobSeekers.stream()
            .filter(jobSeeker -> jobSeeker.email.equals(command.email))
            .findFirst();
    command.replyTo.tell(new GetJobSeekerResponse(maybeJobSeeker));
    return this;
  }

  private Behavior<Command> onDeleteJobSeeker(DeleteJobSeeker command) {
    jobSeekers.removeIf(jobSeeker -> jobSeeker.email.equals(command.email));
    command.replyTo.tell(new ActionPerformed(String.format("JobSeeker %s deleted.", command.email)));
    return this;
  }
}
//#jobseeker-registry-actor
