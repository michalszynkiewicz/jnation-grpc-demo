package org.acme.admin;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.acme.QuizGrpcService;

import io.quarkus.grpc.GrpcService;
import org.acme.riddle.Riddle;
import org.acme.riddle.RiddleService;

@Path("/admin/start")
public class QuizStartResource {

    @GrpcService
    RiddleService riddleService;

    @GET
    public Response startQuiz() {
        riddleService.startQuiz();
        return Response.ok().build();
    }
}
