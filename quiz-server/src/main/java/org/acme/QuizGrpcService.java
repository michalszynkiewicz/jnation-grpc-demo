package org.acme;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.acme.quiz.grpc.Answer;
import org.acme.quiz.grpc.Empty;
import org.acme.quiz.grpc.Question;
import org.acme.quiz.grpc.Quiz;
import org.acme.quiz.grpc.Result;
import org.acme.quiz.grpc.Scores;
import org.acme.quiz.grpc.SignUpRequest;
import org.acme.quiz.grpc.SignUpResponse;
import org.acme.quiz.grpc.UserScore;
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
