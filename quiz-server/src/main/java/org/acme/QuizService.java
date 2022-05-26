package org.acme;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.vertx.mutiny.core.Vertx;
import org.acme.quiz.grpc.Question;
import org.acme.quiz.grpc.QuizGrpcService;
import org.acme.quiz.grpc.Result;
import org.acme.quiz.grpc.Score;
import org.acme.quiz.grpc.SignUpRequest;
import org.acme.quiz.grpc.SignUpResult;
import org.acme.quiz.grpc.Solution;
import org.acme.quiz.grpc.UserScore;
import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@GrpcService
public class QuizService implements QuizGrpcService {
    public static final long TIME_TO_ANSWER = 10_000L;
    private final Map<String, String> userTokens = new ConcurrentHashMap<>();
    private final Map<String, String> usersByToken = new ConcurrentHashMap<>();
    private final Set<String> usersWithResponse = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, AtomicInteger> userScores = new ConcurrentHashMap<>();
    private final BroadcastProcessor<Question> riddleBroadcast = BroadcastProcessor.create();
    private final BroadcastProcessor<Score> scoreBroadcast = BroadcastProcessor.create();
    private final AtomicReference<Riddle> currentRiddle = new AtomicReference<>();

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    Vertx vertx;

    private void sendNextRiddle(int riddleIdx) {
        if (riddleIdx > 0) {
            sessionFactory.openStatelessSession()
                    .onItem().transformToUni(
                            session ->
                                    session.createNativeQuery(
                                                    "select * from riddles order by id limit 1 offset ?1", Riddle.class)
                                            .setParameter(1, riddleIdx - 1)
                                            .getSingleResult()
                                            .onItem().transformToUni(r -> session.fetch(r.answers).replaceWith(r))
                                            .onItem().transformToUni(r -> session.close().replaceWith(r))
                    ).onItem().invoke(currentRiddle::set)
                    .onItem().transform(Riddle::toGrpcQuestion)
                    .onItem().invoke(riddleBroadcast::onNext)
                    .onItem().invoke(this::broadcastScores)
                    .onItem().invoke(usersWithResponse::clear)
                    .subscribe().with(whatever ->
                            vertx.setTimer(TIME_TO_ANSWER, ignored -> sendNextRiddle(riddleIdx - 1))
                    );
        } else {
            broadcastScores();
            riddleBroadcast.onNext(Question.newBuilder().setText("That's it, thanks for playing!").build());
        }

    }

    private void broadcastScores() {
        Score.Builder scoreBuilder = Score.newBuilder();
        List<UserScore> userScores = this.userScores.entrySet().stream().map(
                entry ->
                        UserScore.newBuilder()
                                .setUser(entry.getKey())
                                .setPoints(entry.getValue().get())
                                .build()
        ).collect(Collectors.toList());
        scoreBuilder.addAllScores(userScores);
        scoreBroadcast.onNext(scoreBuilder.build());
    }

    private Uni<Integer> countRiddles(Mutiny.StatelessSession session) {
        return session.createQuery("select count(*) from Riddle ", Long.class)
                .getSingleResult().map(Long::intValue);
    }

    @Override
    public Uni<SignUpResult> signUp(SignUpRequest request) {
        String token = RandomStringUtils.randomAlphanumeric(10);
        String name = request.getName();
        if (userTokens.putIfAbsent(name, token) != null) {
            return Uni.createFrom().item(
                    SignUpResult.newBuilder().setResult(SignUpResult.Result.NAME_ALREADY_USED).build()
            );
        }
        usersByToken.put(token, name);
        userScores.put(name, new AtomicInteger(0));
        return Uni.createFrom().item(SignUpResult.newBuilder().setResult(SignUpResult.Result.OKAY).setToken(token).build());
    }

    @Override
    public Uni<Empty> start(Empty request) {
        sessionFactory.openStatelessSession()
                .onItem().transformToUni(this::countRiddles)
                .subscribe().with(cnt -> sendNextRiddle(cnt));
        return Uni.createFrom().item(Empty.getDefaultInstance());
    }

    @Override
    public Uni<Result> answer(Solution request) {
        Result.Builder result = Result.newBuilder();
        Riddle currentRiddle = this.currentRiddle.get();

        if (!usersWithResponse.add(request.getToken())) {
            result.setStatus(Result.Status.DUPLICATE_ANSWER);
        } else if (request.getRiddleId().equals(currentRiddle.id)) {
            if (request.getSolution().equals(currentRiddle.answer)) {
                String userName = usersByToken.get(request.getToken());
                userScores.get(userName).incrementAndGet();
                result.setStatus(Result.Status.OKAY);
            } else {
                result.setStatus(Result.Status.WRONG);
            }
        } else {
            result.setStatus(Result.Status.TIMEOUT);
        }
        return Uni.createFrom().item(result.build());
    }

    @Override
    public Multi<Question> getRiddles(Empty request) {
        if (currentRiddle.get() != null) {
            return Multi.createBy().merging().streams(Multi.createFrom().item(currentRiddle.get().toGrpcQuestion()), riddleBroadcast);
        } else {
            return riddleBroadcast;
        }
    }

    @Override
    public Multi<Score> watchScore(Empty request) {
        return scoreBroadcast;
    }
}
