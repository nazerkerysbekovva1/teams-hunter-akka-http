package com.example.Auth;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.example.User.EmployerRegistry;
import com.example.User.JobSeekerRegistry;
import com.example.User.UserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class SignUp {
    private final static Logger log = LoggerFactory.getLogger(com.example.User.UserRoutes.class);
    private final ActorRef<UserRegistry.Command> userRegistryActor;
    private final ActorRef<JobSeekerRegistry.Command> jobSeekerRegistryActor;
    private final ActorRef<EmployerRegistry.Command> employerRegistryActor;
    private final Duration askTimeout;
    private final Scheduler scheduler;

    //UserRoutes
    public SignUp(
            ActorSystem<?> system,
            ActorRef<UserRegistry.Command> userRegistryActor,
            ActorRef<JobSeekerRegistry.Command> jobSeekerRegistryActor,
            ActorRef<EmployerRegistry.Command> employerRegistryActor)
    {
        this.userRegistryActor = userRegistryActor;
        this.jobSeekerRegistryActor = jobSeekerRegistryActor;
        this.employerRegistryActor = employerRegistryActor;
        scheduler = system.scheduler();
        askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    private CompletionStage<UserRegistry.ActionPerformed> createUser(UserRegistry.User user) {
        String role = user.getRole();
        createUserAll(user);
        if (role != null) {
            if (role.equalsIgnoreCase("jobseeker")) {
                JobSeekerRegistry.JobSeeker jobSeeker = new JobSeekerRegistry.JobSeeker(user.getName(), user.getEmail(), user.getPassword());
                return createJobSeeker(jobSeeker);
            } else if (role.equalsIgnoreCase("employer")) {
                EmployerRegistry.Employer employer = new EmployerRegistry.Employer(user.getName(), user.getEmail(), user.getPassword());
                return createEmployer(employer);
            } else {
                return CompletableFuture.completedFuture(new UserRegistry.ActionPerformed("Verify role"));
            }
        } else {
            // Handle the case when the role is null
            return CompletableFuture.completedFuture(new UserRegistry.ActionPerformed("Role is null"));
        }
    }

    private CompletionStage<UserRegistry.ActionPerformed> createJobSeeker(JobSeekerRegistry.JobSeeker jobSeeker) {
        return AskPattern.ask(jobSeekerRegistryActor, ref -> new JobSeekerRegistry.CreateJobSeeker(jobSeeker, ref), askTimeout, scheduler);
    }
    private CompletionStage<UserRegistry.ActionPerformed> createEmployer(EmployerRegistry.Employer employer) {
        return AskPattern.ask(employerRegistryActor, ref -> new EmployerRegistry.CreateEmployer(employer, ref), askTimeout, scheduler);
    }
    private CompletionStage<UserRegistry.ActionPerformed> createUserAll(UserRegistry.User user) {
        return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.CreateUser(user, ref), askTimeout, scheduler);
    }


    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    //#all-routes auth
    public Route signUpRoutes() {
        return pathPrefix("sign-up", () ->
                concat(
                        pathEnd(() ->
                                concat(
                                        post(() ->
                                                entity(
                                                        Jackson.unmarshaller(UserRegistry.User.class),
                                                        user ->
                                                                onSuccess(createUser(user), performed -> {
                                                                    log.info("Create result: {}", performed.description);
                                                                    return complete(StatusCodes.CREATED, performed, Jackson.marshaller());
                                                                })
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
