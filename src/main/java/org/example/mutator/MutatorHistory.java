package org.example.mutator;

import java.util.ArrayList;
import java.util.List;

public class MutatorHistory {

    private List<MutationOperator> appliedMutations = new ArrayList<>();

    public MutatorHistory(){}

    public List<MutationOperator> getAppliedMutations() {
        return appliedMutations;
    }

    public void addAppliedMutation(MutationOperator newMutation){
        appliedMutations.add(newMutation);
    }
}
