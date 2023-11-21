package com.example.Auth;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.http.javadsl.server.Route;
import com.example.User.EmployerRegistry;
import com.example.User.JobSeekerRegistry;
import com.example.User.UserRegistry;

import java.time.Duration;

import static akka.http.javadsl.server.Directives.*;

public class AuthRoutes {
    private final ActorRef<UserRegistry.Command> userRegistryActor;
    private final ActorRef<JobSeekerRegistry.Command> jobSeekerRegistryActor;
    private final ActorRef<EmployerRegistry.Command> employerRegistryActor;
    private final UserRegistry userRegistry; // Add a field for UserRegistry
    private final Duration askTimeout;
    private final Scheduler scheduler;
    private final ActorSystem<?> system;

    public AuthRoutes(
            ActorSystem<?> system,
            ActorRef<UserRegistry.Command> userRegistryActor,
            ActorRef<JobSeekerRegistry.Command> jobSeekerRegistryActor,
            ActorRef<EmployerRegistry.Command> employerRegistryActor,
            UserRegistry userRegistry) { // Receive UserRegistry as a parameter
        this.system = system;
        this.userRegistryActor = userRegistryActor;
        this.jobSeekerRegistryActor = jobSeekerRegistryActor;
        this.employerRegistryActor = employerRegistryActor;
        this.userRegistry = userRegistry; // Initialize the userRegistry field
        this.scheduler = system.scheduler();
        this.askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    public Route authRoutes(){
        Logout logout = new Logout();

        return pathPrefix("auth", () ->
                concat(
                        new SignUp(system, userRegistryActor, jobSeekerRegistryActor, employerRegistryActor).signUpRoutes(),
                        new SignIn(system, userRegistryActor, userRegistry).signInRoutes(),
                        logout.logoutRoutes()
                )
        );
    }
}
