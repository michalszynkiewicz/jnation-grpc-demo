package org.acme;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
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

    private static final long DELAY = 10_000L;
    private final UnicastProcessor<Question> questionUnicast = UnicastProcessor.create();
    private final BroadcastProcessor<Scores> scoresBroadcast = BroadcastProcessor.create();
    private final Multi<Question> questionBroadcast =
            Multi.createBy().replaying().upTo(1)
                    .ofMulti(questionUnicast)
                    .broadcast().toAllSubscribers();

    private final AtomicReference<Riddle> currentRiddle = new AtomicReference<>();

    private final Map<String, Integer> userScores = new ConcurrentHashMap<>();
    private final Set<String> usersWithResponse = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    RiddleStorage riddleStorage;

    @Inject
    Vertx vertx;

    @Override
    public Uni<Empty> start(Empty request) {
        broadcastQuestion(0);
        return Uni.createFrom().item(Empty.getDefaultInstance());
    }

    @Override
    public Uni<Result> respond(Answer answer) {
        Result.Builder result = Result.newBuilder();
        if (currentRiddle.get() == null || !currentRiddle.get().text.equals(answer.getQuestion())) {
            result.setStatus(Result.Status.TIMEOUT);
        } else if (!usersWithResponse.add(answer.getUser())) {
            result.setStatus(Result.Status.DUPLICATE_ANSWER);
        } else if (currentRiddle.get().answer.equals(answer.getText())) {
            userScores.put(answer.getUser(), userScores.get(answer.getUser()) + 1);
            result.setStatus(Result.Status.CORRECT);
        } else {
            result.setStatus(Result.Status.WRONG);
        }
        return Uni.createFrom().item(result.build());
    }

    @Override
    public Uni<SignUpResponse> signUp(SignUpRequest request) {
            SignUpResponse.Status status;
        if (userScores.putIfAbsent(request.getName(), 0) != null) {
            status = SignUpResponse.Status.NAME_TAKEN;
        } else {
            status = SignUpResponse.Status.OKAY;
        }
        return Uni.createFrom().item(SignUpResponse.newBuilder().setStatus(status).build());
    }

    @Override
    public Multi<Question> getQuestions(Empty request) {
        return questionBroadcast;
    }

    @Override
    public Multi<Scores> watchScore(Empty request) {
        return scoresBroadcast;
    }

    private void broadcastQuestion(int i) {
        Riddle riddle = riddleStorage.getRiddle(i);
        if (riddle != null) {
            questionUnicast.onNext(riddle.toQuestion());
            currentRiddle.set(riddle);
            vertx.setTimer(DELAY, ignored -> broadcastQuestion(i + 1));
        } else {
            currentRiddle.set(null);
            questionUnicast.onNext(Question.newBuilder().setText("That's all, thanks for playing!").build());
        }

        Scores.Builder scores = Scores.newBuilder();
        userScores.forEach(
                (user, score) ->
                        scores.addResults(UserScore.newBuilder().setUser(user).setPoints(score).build())
        );
        scoresBroadcast.onNext(scores.build());
    }
}
