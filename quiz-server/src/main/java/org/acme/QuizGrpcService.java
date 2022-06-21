package org.acme;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import org.acme.riddle.Riddle;
import org.acme.riddle.RiddleService;
import org.acme.score.ScoreService;

import javax.inject.Inject;

public class QuizGrpcService  {

    @Inject
    RiddleService riddleService;

    @Inject
    ScoreService scoreService;

}
