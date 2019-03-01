package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, ValueWithContext}

object FlinkCustomStreamTransformation {
  def apply(fun: DataStream[Context] => DataStream[ValueWithContext[Any]]): FlinkCustomStreamTransformation
  = apply((data, _) => fun(data))

  def apply(fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[Any]])
  : FlinkCustomStreamTransformation = new FlinkCustomStreamTransformation {
    override def transform(start: DataStream[Context], context: FlinkCustomNodeContext)
    : DataStream[ValueWithContext[Any]] = fun(start, context)
  }

  def apply(fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[Any]],
            rType: TypingResult): FlinkCustomStreamTransformation with ReturningType =
    new FlinkCustomStreamTransformation with ReturningType {
      override def transform(start: DataStream[Context], context: FlinkCustomNodeContext)
      : DataStream[ValueWithContext[Any]] = fun(start, context)

      override def returnType: typing.TypingResult = rType
    }
}

trait FlinkCustomStreamTransformation {

  def transform(start: DataStream[Context], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]]

}


/**
  * Join functionality is not complete, many things are not implemented yet
  * - no way of declaring edge id
  * - there should be some possibility of adding expressions/configurations on edges to join
  * - output of join is Unknown - probably it will depend on inputs...
  * - no way of declaring what variables will be passed from join (e.g. union has different semantic than join?)
  * - cannot test&generate test data from other branches
  *
  * Additionally, a lot of refactoring should be done
  * - removing tree structures
  * - should CustomNode and Join be sth different in ConfigCreator
  *
  * Some important TODOs are marked with TODO JOIN
  */
trait FlinkCustomJoinTransformation {


  def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]]

}