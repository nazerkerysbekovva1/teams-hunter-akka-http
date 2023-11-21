package com.example;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorSystem;
import com.example.Auth.AuthRoutes;
import com.example.User.EmployerRegistry;
import com.example.User.JobSeekerRegistry;
import com.example.User.UserRegistry;
import com.example.User.UserRoutes;
import static akka.http.javadsl.server.Directives.*;


import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

//#main-class
public class StartApp {
    // #start-http-server
    static void startHttpServer(Route route, ActorSystem<?> system) {
        CompletionStage<ServerBinding> futureBinding =
            Http.get(system).newServerAt("localhost", 8080).bind(route);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}/",
                    address.getHostString(),
                    address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }
    // #start-http-server

    public static void main(String[] args) throws Exception {
        //#server-bootstrapping
        Behavior<UserRegistry.Command> rootBehavior = Behaviors.setup(context -> {
            ActorRef<UserRegistry.Command> userRegistryActor =
                context.spawn(UserRegistry.create(), "UserRegistry");

            ActorRef<JobSeekerRegistry.Command> jobSeekerRegistryActor =
                    context.spawn(JobSeekerRegistry.create(), "JobSeekerRegistry");

            ActorRef<EmployerRegistry.Command> employerRegistryActor =
                    context.spawn(EmployerRegistry.create(), "EmployerRegistry");

            UserRegistry userRegistry = new UserRegistry(context);
            UserRoutes userRoutes = new UserRoutes(context.getSystem(), userRegistryActor);
            AuthRoutes authRoutes = new AuthRoutes(context.getSystem(), userRegistryActor, jobSeekerRegistryActor, employerRegistryActor, userRegistry);

            // Combine userRoutes and authRoutes into a single route
            Route combinedRoute = pathPrefix("api", () ->
                    concat(
                            userRoutes.userRoutes(),
                            authRoutes.authRoutes()
                    )
            );

            // Start the HTTP server with the combined route
            startHttpServer(combinedRoute, context.getSystem());

            return Behaviors.empty();
        });

        // boot up server using the route as defined below
        ActorSystem.create(rootBehavior, "HelloAkkaHttpServer");
        //#server-bootstrapping
    }

}
//#main-class


