package org.acme;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import org.acme.quiz.grpc.Answer;
import org.acme.quiz.grpc.Question;
import org.acme.quiz.grpc.Quiz;
import org.acme.quiz.grpc.Result;
import org.acme.quiz.grpc.SignUpRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
public class QuizServiceTest {

    @GrpcClient
    Quiz quizClient;

    @Test
    void shouldAnswerAQuestion() {
        quizClient.signUp(SignUpRequest.newBuilder().setName("Michał").build())
                .await().atMost(Duration.ofSeconds(5));

        Multi<Question> questions = quizClient.getQuestions(Empty.getDefaultInstance());

        AtomicReference<Question> question = new AtomicReference<>();
        questions.subscribe().with(question::set);

        quizClient.start(Empty.getDefaultInstance()).await().atMost(Duration.ofSeconds(10));
        await().until(() -> question.get() != null);

        String questionText = question.get().getText();
        Answer answer = Answer.newBuilder().setQuestion(questionText).setText("5").setUser("Michał").build();
        Result result = quizClient.respond(answer)
                .await().atMost(Duration.ofSeconds(10));

        assertThat(result.getStatus()).isEqualTo(Result.Status.CORRECT);
    }
}
