package pl.touk.nussknacker.engine.spel.parser;

import org.springframework.expression.Expression;
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange;

import java.util.Optional;

public class SingleExpressionWithTextRange implements ExpressionWithTextRange {
    private final Expression expression;
    private final IndexBasedTextRange textRange;

    SingleExpressionWithTextRange(Expression expression, IndexBasedTextRange textRange) {
        this.expression = expression;
        this.textRange = textRange;
    }

    @Override
    public Optional<SingleExpressionWithTextRange> findSubexpressionByPosition(int position) {
        if (textRange.containsPosition(position)) {
            return Optional.of(this);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Expression getExpression() {
        return expression;
    }

    @Override
    public IndexBasedTextRange getTextRange() {
        return textRange;
    }
}
