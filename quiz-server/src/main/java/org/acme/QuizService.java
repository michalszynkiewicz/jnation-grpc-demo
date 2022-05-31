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
import org.acme.quiz.grpc.Result;
import org.acme.quiz.grpc.Scores;
import org.acme.quiz.grpc.SignUpRequest;
import org.acme.quiz.grpc.SignUpResponse;
import org.acme.quiz.grpc.UserScore;

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
    final BroadcastProcessor<Scores> scoresBroadcast = BroadcastProcessor.create();

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
    public Multi<Scores> watchScore(Empty request) {
        return scoresBroadcast;
    }

    @Override
    public Uni<Empty> start(Empty request) {
        broadcastQuestions(0);
        return Uni.createFrom().item(Empty.getDefaultInstance());
    }

    @Override
    public Uni<Result> respond(Answer request) {
        Result.Builder response = Result.newBuilder();
        if (!request.getQuestion().equals(currentRiddle.get().text)) {
            response.setStatus(Result.Status.TIMEOUT);
        } else if (!usersWithResponses.add(request.getUser())) {
            response.setStatus(Result.Status.DUPLICATE_ANSWER);
        } else if (currentRiddle.get().answer.equals(request.getText())) {
            response.setStatus(Result.Status.CORRECT);
            pointsByUser.put(request.getUser(), pointsByUser.get(request.getUser()) + 1);
        } else {
            response.setStatus(Result.Status.WRONG);
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

        Scores.Builder results = Scores.newBuilder();
        pointsByUser.forEach(
                (name, points) -> results.addResults(UserScore.newBuilder().setUser(name).setPoints(points).build())
        );
        scoresBroadcast.onNext(results.build());
    }
}
