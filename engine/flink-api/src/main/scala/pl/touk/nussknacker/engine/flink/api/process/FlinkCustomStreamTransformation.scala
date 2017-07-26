package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.{InterpretationResult, ValueWithContext}

object FlinkCustomStreamTransformation {
  def apply(fun: (DataStream[InterpretationResult], FlinkCustomNodeContext) => DataStream[ValueWithContext[Any]])
  : FlinkCustomStreamTransformation = new FlinkCustomStreamTransformation {
    override def transform(start: DataStream[InterpretationResult], context: FlinkCustomNodeContext)
    : DataStream[ValueWithContext[Any]] = fun(start, context)
  }

  def apply(fun: DataStream[InterpretationResult] => DataStream[ValueWithContext[Any]]) : FlinkCustomStreamTransformation
    = apply((data, _) => fun(data))
}

trait FlinkCustomStreamTransformation {

  def transform(start: DataStream[InterpretationResult], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]]

}
