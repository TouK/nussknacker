package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.AggregateFunction
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue

/**
 * This class unwraps value from input's KeyedValue. It also accumulate first Nussknacker's context that will be passed in output at the end.
 */
class UnwrappingAggregateFunction(underlying: AggregateFunction[AnyRef, AnyRef, AnyRef]) extends AggregateFunction[ValueWithContext[StringKeyedValue[AnyRef]], AccumulatorWithContext, ValueWithContext[AnyRef]] {

  override def createAccumulator(): AccumulatorWithContext = AccumulatorWithContext(underlying.createAccumulator(), None)

  override def add(value: ValueWithContext[StringKeyedValue[AnyRef]], accumulator: AccumulatorWithContext): AccumulatorWithContext = {
    val underlyingAcc = underlying.add(value.value.value, accumulator.value)
    val firstContext = accumulator.context.getOrElse(value.context)
    AccumulatorWithContext(underlyingAcc, Some(firstContext))
  }

  override def getResult(accumulator: AccumulatorWithContext): ValueWithContext[AnyRef] = {
    val accCtx = accumulator.context.get
    ValueWithContext(underlying.getResult(accumulator.value), accCtx)
  }

  override def merge(a: AccumulatorWithContext, b: AccumulatorWithContext): AccumulatorWithContext = {
    val underlyingAcc = underlying.merge(a.value, b.value)
    val firstContext = a.context.orElse(b.context)
    AccumulatorWithContext(underlyingAcc, firstContext)
  }

}

case class AccumulatorWithContext(value: AnyRef, context: Option[Context])