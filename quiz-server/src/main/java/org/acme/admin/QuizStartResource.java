package org.acme.admin;

import io.quarkus.grpc.GrpcService;
import io.vertx.core.Vertx;
import org.acme.QuizService;
import org.acme.Riddle;
import org.acme.RiddleStorage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.time.Duration;

@Path("/admin/start")
public class QuizStartResource {

    @ConfigProperty(name = "quiz.delay", defaultValue = "2s")
    Duration delay;
    private final RiddleStorage riddleStorage;
    private final QuizService quizService;

    private final Vertx vertx;

    @Inject
    public QuizStartResource(@GrpcService QuizService quizService, RiddleStorage riddleStorage, Vertx vertx) {
        this.quizService = quizService;
        this.riddleStorage = riddleStorage;
        this.vertx = vertx;
    }

    @GET
    public Response startQuiz() {
        broadcastQuestion(0);
        return Response.ok().build();
    }

    private void broadcastQuestion(int i) {
        Riddle riddle = riddleStorage.getRiddle(i);
        if (riddle == null) {
            quizService.endQuiz();
        } else {
            quizService.broadcastQuestion(riddle);
            vertx.setTimer(delay.toMillis(), ignored -> broadcastQuestion(i + 1));
        }
    }
}

