package org.acme;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import org.acme.quiz.grpc.Answer;
import org.acme.quiz.grpc.Empty;
import org.acme.quiz.grpc.Question;
import org.acme.quiz.grpc.Quiz;
import org.acme.quiz.grpc.Result;
import org.acme.quiz.grpc.Scores;
import org.acme.quiz.grpc.SignUpRequest;
import org.acme.quiz.grpc.SignUpResponse;
import org.acme.quiz.grpc.UserScore;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@GrpcService
public class QuizService implements Quiz {

    private final AtomicReference<Riddle> currentRiddle = new AtomicReference<>();

    private final UnicastProcessor<Question> questionBroadcast = UnicastProcessor.create();
    private final Multi<Question> questions =
            Multi.createBy().replaying().upTo(1).ofMulti(questionBroadcast)
                    .broadcast().toAllSubscribers();

    private final BroadcastProcessor<Scores> scoreBroadcast = BroadcastProcessor.create();

    private final Map<String, Integer> pointsByUser = new ConcurrentHashMap<>();
    private final Set<String> usersWithAnswer = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public Uni<Result> respond(Answer request) {
        Riddle riddle = currentRiddle.get();
        Result.Status status;
        if (riddle == null || !riddle.text.equals(request.getQuestion())) {
            status = Result.Status.TIMEOUT;
        } else {
            String user = request.getUser();
            if (!usersWithAnswer.add(user)) {
                status = Result.Status.DUPLICATE_ANSWER;
            } else if (!riddle.answer.equals(request.getText())) {
                status = Result.Status.WRONG;
            } else {
                status = Result.Status.CORRECT;
                pointsByUser.put(user, pointsByUser.get(user) + 1);
            }
        }
        return Uni.createFrom().item(
                Result.newBuilder().setStatus(status).build()
        );
    }

    @Override
    public Uni<SignUpResponse> signUp(SignUpRequest request) {
        SignUpResponse.Status status;
        if (pointsByUser.putIfAbsent(request.getName(), 0) != null) {
            status = SignUpResponse.Status.NAME_TAKEN;
        } else {
            status = SignUpResponse.Status.OKAY;
        }
        return Uni.createFrom().item(SignUpResponse.newBuilder().setStatus(status).build());
    }

    @Override
    public Multi<Scores> watchScore(Empty request) {
        return scoreBroadcast;
    }

    @Override
    public Multi<Question> getQuestions(Empty request) {
        return questions;
    }

    public void broadcastQuestion(Riddle riddle) {
        questionBroadcast.onNext(riddle.toQuestion());
        currentRiddle.set(riddle);
        broadcastResults();
        usersWithAnswer.clear();
    }

    public void endQuiz() {
        questionBroadcast.onNext(Question.newBuilder().setText("That's all, thanks for playing!").build());
        currentRiddle.set(null);
        broadcastResults();
    }

    private void broadcastResults() {
        Scores.Builder scores = Scores.newBuilder();
        pointsByUser.forEach((user, points) ->
                scores.addResults(UserScore.newBuilder().setUser(user).setPoints(points).build()));
        scoreBroadcast.onNext(scores.build());
    }
}
