package pl.touk.nussknacker.engine.demo.custom

import io.circe.generic.JsonCodec
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.demo.Transaction
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

/** Sums all-time transaction amount for each client */
class TransactionAmountAggregator extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[AggregatedAmount])
  def execute(@ParamName("clientId") clientId: LazyParameter[String]): FlinkCustomStreamTransformation = {
    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      start
        .map(ctx.lazyParameterHelper.lazyMapFunction(clientId))
        .keyBy(_.value)
        .mapWithState[ValueWithContext[AnyRef], AggregatedAmount] {
        case (ValueWithContext(_, context), Some(aggregationSoFar)) =>
          val transaction = context.apply[Transaction]("input")
          val aggregationResult = aggregationSoFar.copy(amount = aggregationSoFar.amount + transaction.amount,
            lastTransaction = transaction.eventDate)
          (ValueWithContext(aggregationResult, context), Some(aggregationResult))
        case (ValueWithContext(_, context), None) =>
          val transaction = context.apply[Transaction]("input")
          val aggregationResult = AggregatedAmount(transaction.clientId, transaction.amount, transaction.eventDate)
          (ValueWithContext(aggregationResult, context), Some(aggregationResult))
      }
    })
  }
}

@JsonCodec case class AggregatedAmount(clientId: String, amount: Int, lastTransaction: Long) extends DisplayJsonWithEncoder[AggregatedAmount]