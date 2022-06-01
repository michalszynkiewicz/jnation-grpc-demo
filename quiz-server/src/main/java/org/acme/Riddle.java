package org.acme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.acme.quiz.grpc.Question;

public class Riddle {

    public final String text;
    public final String answer;
    public final List<String> options;

    public Riddle(String text, String answer, String... otherOptions) {
        this.text = text;
        this.answer = answer;
        this.options = new ArrayList<>();
        Collections.addAll(options, otherOptions);
        options.add(answer);
        options.sort(String::compareTo);
    }

    public Question toQuestion() {
        return Question.newBuilder()
                .setText(text)
                .addAllAnswers(options)
                .build();
    }
}
