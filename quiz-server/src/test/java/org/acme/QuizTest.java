package org.acme;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import org.acme.quiz.grpc.Answer;
import org.acme.quiz.grpc.Quiz;
import org.acme.quiz.grpc.Response;
import org.acme.quiz.grpc.SignUpRequest;
import org.acme.quiz.grpc.SignUpResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class QuizTest {

    @GrpcClient
    Quiz quiz;

    @Test
    void shouldGetResponseOnAnswer() {
        SignUpResponse user = quiz.signUp(SignUpRequest.newBuilder().setName("Michał").build())
                .await().atMost(Duration.ofSeconds(5));
        assertThat(user.getStatus()).isEqualTo(SignUpResponse.Status.OKAY);
        quiz.start(Empty.getDefaultInstance()).await().atMost(Duration.ofSeconds(5));

        Response response = quiz.respond(Answer.newBuilder()
                .setUser("Michał")
                .setText("5").build()).await().atMost(Duration.ofSeconds(5));
        assertThat(response.getStatus()).isEqualTo(Response.Status.CORRECT);
    }

    @Test
    void rainyDayResponses() {
        SignUpResponse user = quiz.signUp(SignUpRequest.newBuilder().setName("Joe").build())
                .await().atMost(Duration.ofSeconds(5));
        assertThat(user.getStatus()).isEqualTo(SignUpResponse.Status.OKAY);
        quiz.start(Empty.getDefaultInstance()).await().atMost(Duration.ofSeconds(5));

        Response response = quiz.respond(Answer.newBuilder()
                .setUser("Joe")
                .setText("4").build()).await().atMost(Duration.ofSeconds(5));
        assertThat(response.getStatus()).isEqualTo(Response.Status.WRONG);
        response = quiz.respond(Answer.newBuilder()
                .setUser("Joe")
                .setText("5").build()).await().atMost(Duration.ofSeconds(5));
        assertThat(response.getStatus()).isEqualTo(Response.Status.DUPLICATE_ANSWER);
    }
}
