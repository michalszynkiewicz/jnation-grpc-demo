package org.acme;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.acme.admin.QuizStartResource;
import org.acme.quiz.grpc.Answer;
import org.acme.quiz.grpc.Empty;
import org.acme.quiz.grpc.Question;
import org.acme.quiz.grpc.Quiz;
import org.acme.quiz.grpc.Result;
import org.acme.quiz.grpc.SignUpRequest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class QuizServiceTest {
    @GrpcClient
    Quiz quizClient;

    @Inject
    QuizStartResource start;

    @Test
    void shouldRespondToQuestion() {
        quizClient.signUp(SignUpRequest.newBuilder().setName("Michał").build())
                .await().atMost(Duration.ofSeconds(5));

        AtomicReference<Question> question = new AtomicReference<>();

        quizClient.getQuestions(Empty.getDefaultInstance())
                .subscribe().with(question::set);
        start.startQuiz();

        await().atMost(Duration.ofSeconds(5)).until(() -> question.get() != null);

        Result result = quizClient.respond(Answer.newBuilder()
                .setQuestion(question.get().getText())
                .setUser("Michał")
                .setText("5")
                .build()).await().atMost(Duration.ofSeconds(5));

        assertThat(result.getStatus()).isEqualTo(Result.Status.CORRECT);
    }
}
