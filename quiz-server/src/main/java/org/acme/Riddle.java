package org.acme;


import org.acme.quiz.grpc.Question;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "riddles")
public class Riddle {
    @Id
    public String id;
    public String question;
    public String answer;
    @ElementCollection
    @CollectionTable(name = "riddle_answers")
    public List<String> answers;

    public Question toGrpcQuestion() {
        Question.Builder riddle = Question.newBuilder().setRiddleId(id)
                .setText(question);
        riddle.addAllResponses(answers);
        return riddle.build();
    }

    @Override
    public String toString() {
        return "RiddleEntity{" +
                "id='" + id + '\'' +
                ", question='" + question + '\'' +
                ", answer='" + answer + '\'' +
                ", answers=" + answers +
                '}';
    }
}
