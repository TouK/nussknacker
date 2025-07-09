package pl.touk.nussknacker.engine.spel.parser;

import org.springframework.expression.Expression;
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange;

import java.util.Optional;

public interface ExpressionWithTextRange {

    Optional<SingleExpressionWithTextRange> findSubexpressionByPosition(int position);

    Expression getExpression();

    IndexBasedTextRange getTextRange();

    static SingleExpressionWithTextRange forSingleExpression(Expression expression, int length) {
        return new SingleExpressionWithTextRange(expression, new IndexBasedTextRange(0, length));
    }

    static ExpressionWithTextRange forCompositeStringExpression(String expressionString, SingleExpressionWithTextRange[] expressionsWithTextRanges) {
        return new CompositeExpressionsWithTextRanges(expressionString, expressionsWithTextRanges);
    }

}
