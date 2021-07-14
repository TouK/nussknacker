package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.AggregateFunction
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyEnricher, StringKeyedValue}

/**
 * This class unwraps value from input's KeyedValue. It also accumulate key that will be passed in output at the end.
 *
 * NOTE: it would be much cleaner if we evaluated aggregateBy here. However, FLINK-10250 prevents us from doing this and we *have* to compute it beforehand
 *
 * When using this class it's important that agggregator, passedType and unwrap must match: unwrap result is of passedType and can be processed by aggregator
 */
class UnwrappingAggregateFunction[Input](aggregator: Aggregator,
                                         passedType: TypingResult,
                                         unwrapAggregatedValue: Input => AnyRef)
  extends AggregateFunction[ValueWithContext[StringKeyedValue[Input]], StringKeyedValue[AnyRef], ValueWithContext[AnyRef]] {

  private val expectedType = aggregator.computeOutputType(passedType)
    .valueOr(msg => throw new IllegalArgumentException(msg))

  override def createAccumulator(): StringKeyedValue[AnyRef] = StringKeyedValue(null, aggregator.createAccumulator())

  override def add(wrappedInput: ValueWithContext[StringKeyedValue[Input]], accumulator: StringKeyedValue[AnyRef]): StringKeyedValue[AnyRef] = {
    wrappedInput.value.mapValue(input => aggregator.add(unwrapAggregatedValue(input), accumulator.value))
  }

  override def getResult(accumulator: StringKeyedValue[AnyRef]): ValueWithContext[AnyRef] = {
    val finalResult = aggregator.alignToExpectedType(aggregator.getResult(accumulator.value), expectedType)
    ValueWithContext(finalResult, KeyEnricher.enrichWithKey(Context(""), accumulator))
  }

  override def merge(a: StringKeyedValue[AnyRef], b: StringKeyedValue[AnyRef]): StringKeyedValue[AnyRef] = {
    val mergedKey = Option(a.key).getOrElse(b.key)
    val mergedValue = aggregator.merge(a.value, b.value)
    StringKeyedValue(mergedKey, mergedValue)
  }

}
