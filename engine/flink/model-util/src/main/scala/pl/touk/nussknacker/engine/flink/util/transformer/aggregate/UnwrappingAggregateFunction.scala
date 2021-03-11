package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.AggregateFunction
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}

/**
 * This class unwraps value from input's KeyedValue. It also accumulate first Nussknacker's context that will be passed in output at the end.
 *
 * NOTE: it would be much cleaner if we evaluated aggregateBy here. However, FLINK-10250 prevents us from doing this and we *have* to compute it before
 */
class UnwrappingAggregateFunction[T](underlying: Aggregator, passedType: TypingResult, unwrap: T => AnyRef, outputContextStrategy: OutputContextStrategy)
  extends AggregateFunction[ValueWithContext[T], AccumulatorWithContext, ValueWithContext[AnyRef]] {

  private val expectedType = underlying.computeOutputType(passedType)
    .valueOr(msg => throw new IllegalArgumentException(msg))

  override def createAccumulator(): AccumulatorWithContext = AccumulatorWithContext(underlying.createAccumulator(), None)

  override def add(value: ValueWithContext[T], accumulator: AccumulatorWithContext): AccumulatorWithContext = {
    val underlyingAcc = underlying.add(unwrap(value.value), accumulator.value)
    val contextToUse = outputContextStrategy.transform(accumulator.context, value.context)
    AccumulatorWithContext(underlyingAcc, contextToUse)
  }

  override def getResult(accumulator: AccumulatorWithContext): ValueWithContext[AnyRef] = {
    val accCtx = accumulator.context.getOrElse(outputContextStrategy.empty)
    val finalResult = underlying.alignToExpectedType(underlying.getResult(accumulator.value), expectedType)
    ValueWithContext(finalResult, accCtx)
  }

  override def merge(a: AccumulatorWithContext, b: AccumulatorWithContext): AccumulatorWithContext = {
    val underlyingAcc = underlying.merge(a.value, b.value)
    val firstContext = a.context.orElse(b.context)
    AccumulatorWithContext(underlyingAcc, firstContext)
  }

}

case class AccumulatorWithContext(value: AnyRef, context: Option[Context])

