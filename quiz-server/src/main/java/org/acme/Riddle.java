package org.acme;

import org.acme.quiz.grpc.Question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Riddle {
    public String text;
    public String answer;
    public List<String> options;

    public Riddle(String text, String answer, String... otherOptions) {
        this.text = text;
        this.answer = answer;
        this.options = new ArrayList<>();
        options.addAll(Arrays.asList(otherOptions));
        options.add(answer);
    }

    public Question toQuestion() {
        List<String> answers = new ArrayList<>(options);
        answers.sort(String::compareTo);

        return Question.newBuilder()
                .setText(text)
                .addAllAnswers(answers)
                .build();
    }
}
