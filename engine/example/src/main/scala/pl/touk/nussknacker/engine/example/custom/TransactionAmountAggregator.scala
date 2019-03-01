package pl.touk.nussknacker.engine.example.custom

import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.example.Transaction
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import argonaut.ArgonautShapeless._

/** Sums all-time transaction amount for each client */
class TransactionAmountAggregator extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[AggregatedAmount])
  def execute(@ParamName("clientId") clientId: LazyParameter[String]): FlinkCustomStreamTransformation = {
    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      start
        .map(ctx.nodeServices.lazyMapFunction(clientId))
        .keyBy(_.value)
        .mapWithState[ValueWithContext[Any], AggregatedAmount] {
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

case class AggregatedAmount(clientId: String, amount: Int, lastTransaction: Long) extends DisplayableAsJson[AggregatedAmount]