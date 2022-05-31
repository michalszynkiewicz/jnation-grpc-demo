package org.acme;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.vertx.core.Vertx;
import org.acme.quiz.grpc.Answer;
import org.acme.quiz.grpc.Question;
import org.acme.quiz.grpc.Quiz;
import org.acme.quiz.grpc.Response;
import org.acme.quiz.grpc.Results;
import org.acme.quiz.grpc.SignUpRequest;
import org.acme.quiz.grpc.SignUpResponse;
import org.acme.quiz.grpc.UserResult;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@GrpcService
public class QuizService implements Quiz {

    final AtomicReference<Riddle> currentRiddle = new AtomicReference<>();
    final BroadcastProcessor<Question> questionBroadcast = BroadcastProcessor.create();
    final BroadcastProcessor<Results> scoresBroadcast = BroadcastProcessor.create();

    final Map<String, Integer> pointsByUser = new ConcurrentHashMap<>();
    final Set<String> usersWithResponses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    RiddleStorage riddleStorage;

    @Inject
    Vertx vertx;

    @Override
    public Multi<Question> getQuestions(Empty request) {
        return questionBroadcast;
    }

    @Override
    public Multi<Results> watchScore(Empty request) {
        return scoresBroadcast;
    }

    @Override
    public Uni<Empty> start(Empty request) {
        broadcastQuestions(0);
        return Uni.createFrom().item(Empty.getDefaultInstance());
    }

    @Override
    public Uni<Response> respond(Answer request) {
        Response.Builder response = Response.newBuilder();
        if (!request.getQuestion().equals(currentRiddle.get().text)) {
            response.setStatus(Response.Status.TIMEOUT);
        } else if (!usersWithResponses.add(request.getUser())) {
            response.setStatus(Response.Status.DUPLICATE_ANSWER);
        } else if (currentRiddle.get().answer.equals(request.getText())) {
            response.setStatus(Response.Status.CORRECT);
            pointsByUser.put(request.getUser(), pointsByUser.get(request.getUser()) + 1);
        } else {
            response.setStatus(Response.Status.WRONG);
        }
        return Uni.createFrom().item(response.build());
    }

    @Override
    public Uni<SignUpResponse> signUp(SignUpRequest request) {
        String name = request.getName();
        SignUpResponse.Status status;
        if (pointsByUser.containsKey(name)) {
            status = SignUpResponse.Status.NAME_TAKEN;
        } else {
            pointsByUser.put(name, 0);
            status = SignUpResponse.Status.OKAY;
        }
        return Uni.createFrom()
                .item(SignUpResponse.newBuilder().setStatus(status).build());
    }

    private void broadcastQuestions(int i) {
        Riddle riddle = riddleStorage.getRiddle(i);
        if (riddle != null) {
            sendQuestion(riddle);
            vertx.setTimer(5000L, ignored -> broadcastQuestions(i + 1));
        } else {
            sendQuestion(new Riddle("Thanks for playing!", ""));
        }
    }

    private void sendQuestion(Riddle riddle) {
        currentRiddle.set(riddle);
        questionBroadcast.onNext(riddle.toQuestion());
        usersWithResponses.clear();

        Results.Builder results = Results.newBuilder();
        pointsByUser.forEach(
                (name, points) -> results.addResults(UserResult.newBuilder().setUser(name).setPoints(points).build())
        );
        scoresBroadcast.onNext(results.build());
    }
}
