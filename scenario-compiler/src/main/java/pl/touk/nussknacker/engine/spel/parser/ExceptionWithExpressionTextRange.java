package pl.touk.nussknacker.engine.spel.parser;

import pl.touk.nussknacker.engine.expression.IndexBasedTextRange;

public class ExceptionWithExpressionTextRange extends RuntimeException {

    private final IndexBasedTextRange expressionTextRange;

    public ExceptionWithExpressionTextRange(String message, IndexBasedTextRange expressionTextRange) {
        super(message);
        this.expressionTextRange = expressionTextRange;
    }

    public ExceptionWithExpressionTextRange(Throwable cause, IndexBasedTextRange expressionTextRange) {
        super(cause.getMessage(), cause);
        this.expressionTextRange = expressionTextRange;
    }

    public IndexBasedTextRange getExpressionTextRange() {
        return expressionTextRange;
    }
}
