package org.acme.admin;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.acme.QuizService;

import io.quarkus.grpc.GrpcService;

@Path("/admin/start")
public class QuizStartResource {

    @GrpcService
    QuizService quizService;

    @GET
    public Response startQuiz() {
        quizService.startQuiz();
        return Response.ok().build();
    }
}
