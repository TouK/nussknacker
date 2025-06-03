package pl.touk.nussknacker.engine.spel.parser;

import org.springframework.expression.Expression;
import org.springframework.expression.common.CompositeStringExpression;
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange;

import java.util.Arrays;
import java.util.Optional;

public class CompositeExpressionsWithTextRanges implements ExpressionWithTextRange {

    private final SingleExpressionWithTextRange[] childExpressionsWithTextRanges;

    private final CompositeStringExpression expression;

    CompositeExpressionsWithTextRanges(String expressionString, SingleExpressionWithTextRange[] childExpressionsWithTextRanges) {
        this.childExpressionsWithTextRanges = childExpressionsWithTextRanges;
        Expression[] expressions = Arrays.stream(childExpressionsWithTextRanges).map(SingleExpressionWithTextRange::getExpression).toArray(Expression[]::new);
        this.expression = new CompositeStringExpression(expressionString, expressions);
    }

    public Optional<SingleExpressionWithTextRange> findSubexpressionByPosition(int position) {
        return Arrays.stream(childExpressionsWithTextRanges).filter(e -> e.getTextRange().start() <= position && position <= e.getTextRange().end()).findFirst();
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public IndexBasedTextRange getTextRange() {
        return new IndexBasedTextRange(0, expression.getExpressionString().length());
    }

    public SingleExpressionWithTextRange[] getChildExpressionsWithTextRanges() {
        return childExpressionsWithTextRanges;
    }
}
