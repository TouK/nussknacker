package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.AggregateFunction
import pl.touk.nussknacker.engine.api.ValueWithContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.KeyedValue

/**
 * This class unwraps value from input's KeyedValue.
 *
 * NOTE: it would be much cleaner if we evaluated aggregateBy here. However, FLINK-10250 prevents us from doing this and we *have* to compute it beforehand
 *
 * When using this class it's important that aggregator, passedType and unwrap must match: unwrap result is of passedType and can be processed by aggregator
 */
class UnwrappingAggregateFunction[Input](
    aggregator: Aggregator,
    passedType: TypingResult,
    unwrapAggregatedValue: Input => AnyRef
) extends AggregateFunction[ValueWithContext[KeyedValue[AnyRef, Input]], AnyRef, AnyRef] {

  private val expectedType = aggregator
    .computeOutputType(passedType)
    .valueOr(msg => throw new IllegalArgumentException(msg))

  override def createAccumulator(): AnyRef = aggregator.createAccumulator()

  override def add(wrappedInput: ValueWithContext[KeyedValue[AnyRef, Input]], accumulator: AnyRef): AnyRef = {
    aggregator.add(unwrapAggregatedValue(wrappedInput.value.value), accumulator)
  }

  override def getResult(accumulator: AnyRef): AnyRef = {
    aggregator.alignToExpectedType(aggregator.getResult(accumulator), expectedType)
  }

  override def merge(a: AnyRef, b: AnyRef): AnyRef = {
    aggregator.merge(a, b)
  }

}
