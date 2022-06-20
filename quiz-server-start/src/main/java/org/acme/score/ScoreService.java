package org.acme.score;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import org.acme.quiz.grpc.Scores;
import org.acme.quiz.grpc.UserScore;
import org.acme.riddle.Riddle;
import org.acme.riddle.RiddleService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class ScoreService {

    private final Map<String, Integer> pointsByUser = new ConcurrentHashMap<>();

    private final BroadcastProcessor<Map<String, Integer>> scoreBroadcast = BroadcastProcessor.create();

    private final Set<String> usersWithAnswer = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final AtomicReference<Riddle> currentRiddle = new AtomicReference<>();

    public Multi<Map<String, Integer>> getScoreBroadcast() {
        return scoreBroadcast;
    }

    private void broadcastResults() {
        scoreBroadcast.onNext(pointsByUser);
    }

    public Uni<Boolean> addUser(String name) {
        if (pointsByUser.putIfAbsent(name, 0) != null) {
            return Uni.createFrom().item(false);
        } else {
            return Uni.createFrom().item(true);
        }
    }

    public Uni<Result> addResponse(String user, String question, String text) {
        Riddle riddle = currentRiddle.get();
        Result result;
        if (riddle == null || !riddle.text.equals(question)) {
            result = Result.TIMEOUT;
        } else {
            if (!usersWithAnswer.add(user)) {
                result = Result.DUPLICATE_ANSWER;
            } else if (!riddle.answer.equals(text)) {
                result = Result.WRONG;
            } else {
                result = Result.CORRECT;
                pointsByUser.put(user, pointsByUser.get(user) + 1);
            }
        }
        return Uni.createFrom().item(result);
    }

    public void replaceRiddle(Riddle riddle) {
        currentRiddle.set(riddle);
        broadcastResults();
        usersWithAnswer.clear();
    }

    public void clearPoints() {
        pointsByUser.replaceAll((k, v) -> 0);
    }
}
