package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.java.tuple
import org.apache.flink.api.java.tuple.Tuple2
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.UnwrappingAggregateFunction.AccumulatorWithContext

/**
 * This class unwraps value from input's KeyedValue. It also accumulate first Nussknacker's context that will be passed in output at the end.
 *
 * NOTE: it would be much cleaner if we evaluated aggregateBy here. However, FLINK-10250 prevents us from doing this and we *have* to compute it beforehand
 *
 * When using this class it's important that agggregator, passedType and unwrap must match: unwrap result is of passedType and can be processed by aggregator
 */
object UnwrappingAggregateFunction {
  //We use Tuple2 here, to create TypeInformation more easily
  type AccumulatorWithContext = Tuple2[AnyRef, Context]
}

class UnwrappingAggregateFunction[T](aggregator: Aggregator,
                                     passedType: TypingResult,
                                     unwrap: T => AnyRef,
                                     outputContextStrategy: OutputContextStrategy)
  extends AggregateFunction[ValueWithContext[T], Tuple2[AnyRef, Context], ValueWithContext[AnyRef]] {

  private val expectedType = aggregator.computeOutputType(passedType)
    .valueOr(msg => throw new IllegalArgumentException(msg))

  override def createAccumulator(): AccumulatorWithContext = new Tuple2(aggregator.createAccumulator(), null)

  override def add(value: ValueWithContext[T], accumulator: AccumulatorWithContext): AccumulatorWithContext = {
    val underlyingAcc = aggregator.add(unwrap(value.value), accumulator.f0)
    val contextToUse = outputContextStrategy.transform(Option(accumulator.f1), value.context)
    new Tuple2(underlyingAcc, contextToUse.orNull)
  }

  override def getResult(accumulator: AccumulatorWithContext): ValueWithContext[AnyRef] = {
    val accCtx = Option(accumulator.f1).getOrElse(outputContextStrategy.empty)
    val finalResult = aggregator.alignToExpectedType(aggregator.getResult(accumulator.f0), expectedType)
    ValueWithContext(finalResult, accCtx)
  }

  override def merge(a: AccumulatorWithContext, b: AccumulatorWithContext): AccumulatorWithContext = {
    val underlyingAcc = aggregator.merge(a.f0, b.f0)
    val firstContext = Option(a.f1).getOrElse(b.f1)
    new tuple.Tuple2[AnyRef, Context](underlyingAcc, firstContext)
  }

}



