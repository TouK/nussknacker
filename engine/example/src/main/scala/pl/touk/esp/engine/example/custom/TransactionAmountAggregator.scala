package pl.touk.esp.engine.example.custom

import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.example.Transaction
import pl.touk.esp.engine.flink.api.process.FlinkCustomStreamTransformation

/** Sums all-time transaction amount for each client */
class TransactionAmountAggregator extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[AggregatedAmount])
  def execute(@ParamName("clientId") clientId: LazyInterpreter[String]) = {
    FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) => {
      start
        .keyBy(clientId.syncInterpretationFunction)
        .mapWithState[ValueWithContext[Any], AggregatedAmount] {
        case (ir, Some(aggregationSoFar)) =>
          val transaction = ir.finalContext.apply[Transaction]("input")
          val aggregationResult = aggregationSoFar.copy(amount = aggregationSoFar.amount + transaction.amount)
          (ValueWithContext(aggregationResult, ir.finalContext), Some(aggregationResult))
        case (ir, None) =>
          val transaction = ir.finalContext.apply[Transaction]("input")
          val aggregationResult = AggregatedAmount(transaction.clientId, transaction.amount)
          (ValueWithContext(aggregationResult, ir.finalContext), Some(aggregationResult))
      }
    })
  }
}

case class AggregatedAmount(clientId: String, amount: Int)