package org.acme;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RiddleStorage {
    private static final List<Riddle> riddles = new ArrayList<>();

    static {
        riddles.add(new Riddle("How much is 2+3?", "5", "1", "6"));
        riddles.add(new Riddle("What is #000000 in RGB", "black", "white", "yellow"));
        riddles.add(new Riddle("How tall is Jack Reacher", "195", "180", "200"));
    }

    public Riddle getRiddle(int riddleNumber) {
        return riddleNumber < riddles.size() ? riddles.get(riddleNumber) : null;
    }

}
