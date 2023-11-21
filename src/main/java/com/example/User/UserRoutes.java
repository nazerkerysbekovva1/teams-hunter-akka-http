package com.example.User;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import com.example.User.UserRegistry.User;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;

import static akka.http.javadsl.server.Directives.*;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes can be defined in separated classes like shown in here
 */
//#user-routes-class
public class UserRoutes {
  //#user-routes-class
  private final static Logger log = LoggerFactory.getLogger(UserRoutes.class);
  private final ActorRef<UserRegistry.Command> userRegistryActor;
  private final Duration askTimeout;
  private final Scheduler scheduler;

  //UserRoutes
  public UserRoutes(
          ActorSystem<?> system,
          ActorRef<UserRegistry.Command> userRegistryActor)
  {
      this.userRegistryActor = userRegistryActor;
      scheduler = system.scheduler();
      askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
  }

  private CompletionStage<UserRegistry.GetUserResponse> getUser(String email) {
    return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.GetUser(email, ref), askTimeout, scheduler);
  }

  private CompletionStage<UserRegistry.ActionPerformed> deleteUser(String email) {
    return AskPattern.ask(userRegistryActor, ref -> new UserRegistry.DeleteUser(email, ref), askTimeout, scheduler);
  }

  private CompletionStage<UserRegistry.Users> getUsers() {
    return AskPattern.ask(userRegistryActor, UserRegistry.GetUsers::new, askTimeout, scheduler);
  }


    /**
   * This method creates one route (of possibly many more that will be part of your Web App)
   */
  //#all-routes users
  public Route userRoutes() {
    return pathPrefix("users", () ->
        concat(
            //#users-get-delete
            pathEnd(() ->
                concat(
                        get(() ->
                                onSuccess(getUsers(),
                                        users -> complete(StatusCodes.OK, users, Jackson.marshaller())
                                )
                        )
                )
            ),
            //#users-get-delete
            //#users-get-post
            path(PathMatchers.segment(), (String email) ->
                concat(
                    get(() ->
                            //#retrieve-user-info
                            rejectEmptyResponse(() ->
                                    onSuccess(getUser(email), performed -> {
                                        if (performed.maybeUser.isPresent()) {
                                            User user = performed.maybeUser.get();
                                            log.info("Get result: {}", user);
                                            return complete(StatusCodes.OK, user, Jackson.marshaller());
                                        } else {
                                            return complete(StatusCodes.NOT_FOUND, "User not found");
                                        }
                                    })
                            )
                        //#retrieve-user-info
                    ),
                    delete(() ->
                            //#users-delete-logic
                            onSuccess(deleteUser(email), performed -> {
                                  log.info("Delete result: {}", performed.description);
                                  return complete(StatusCodes.OK, performed, Jackson.marshaller());
                                }
                            )
                        //#users-delete-logic
                    )
                )
            )
            //#users-get-post
        )
    );
  }
  //#all-routes

}
