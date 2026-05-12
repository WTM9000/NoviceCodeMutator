package org.example.model;

import java.util.List;

public class DeclarationStatementGroup {

    // Весь DeclarationStatement (int a = 1, b = 2, c;)
    private final StatementNode declarationStatement;

    // Все декларации внутри этого statement
    private final List<SingleDeclarator> declarators;

    public DeclarationStatementGroup(StatementNode declarationStatement,
                                     List<SingleDeclarator> declarators) {
        this.declarationStatement = declarationStatement;
        this.declarators = declarators;
    }

    public StatementNode getDeclarationStatement() { return declarationStatement; }
    public List<SingleDeclarator> getDeclarators() { return declarators; }

    // Все декларации имеют инициализатор
    public boolean allHaveInitializer() {
        return declarators.stream().allMatch(SingleDeclarator::isHasInitializer);
    }

    // Все декларации без инициализатора
    public boolean noneHaveInitializer() {
        return declarators.stream().noneMatch(SingleDeclarator::isHasInitializer);
    }

    // Смешанный случай: есть и с инициализатором, и без
    public boolean isMixed() {
        return !allHaveInitializer() && !noneHaveInitializer();
    }

    @Override
    public String toString() {
        return "DeclarationStatementGroup{" +
                "declarationStatement=" + declarationStatement +
                ", declarators=" + declarators +
                '}';
    }
}
