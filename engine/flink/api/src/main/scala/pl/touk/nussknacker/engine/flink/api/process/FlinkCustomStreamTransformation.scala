package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, ValueWithContext}

object FlinkCustomStreamTransformation {
  def apply(fun: DataStream[Context] => DataStream[ValueWithContext[AnyRef]]): FlinkCustomStreamTransformation
  = apply((data, _) => fun(data))

  def apply(fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[AnyRef]])
  : FlinkCustomStreamTransformation = new FlinkCustomStreamTransformation {
    override def transform(start: DataStream[Context], context: FlinkCustomNodeContext)
    : DataStream[ValueWithContext[AnyRef]] = fun(start, context)
  }

  def apply(fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[AnyRef]], states: Map[String, TypeInformation[_]])
  : FlinkCustomStreamTransformation = new FlinkCustomStreamTransformation {
    override def transform(start: DataStream[Context], context: FlinkCustomNodeContext)
    : DataStream[ValueWithContext[AnyRef]] = fun(start, context)
    override def queryableStateTypes(): Map[String, TypeInformation[_]] = states
  }

  def apply(fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[AnyRef]],
            rType: TypingResult): FlinkCustomStreamTransformation with ReturningType =
    new FlinkCustomStreamTransformation with ReturningType {
      override def transform(start: DataStream[Context], context: FlinkCustomNodeContext)
      : DataStream[ValueWithContext[AnyRef]] = fun(start, context)

      override def returnType: typing.TypingResult = rType
    }
}

trait FlinkCustomStreamTransformation {

  // TODO: To be consistent with ContextTransformation should return Context
  def transform(start: DataStream[Context], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]]

  //this will be used by FlinkQueryableClient
  def queryableStateTypes(): Map[String, TypeInformation[_]] = Map.empty

}


/**
  * Join functionality is not complete, many things are not implemented yet
  * - validation context passed to both BranchExpression and JoinContextTransformationDef should be taken from incoming branches
  * - cannot test&generate test data from other branches
  *
  * Additionally, a lot of refactoring should be done
  * - removing tree structures
  * - should CustomNode and Join be sth different in ConfigCreator
  *
  * Some important TODOs are marked with TODO JOIN
  */
trait FlinkCustomJoinTransformation {

  // TODO: To be consistent with ContextTransformation should return Context
  def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]]

}