package pl.touk.nussknacker.engine.flink.api.process

import cats.data.NonEmptyList
import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.{ComponentOutput, SupportsMultipleOutputs}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object FlinkMultiOutputStreamTransformation {

  def apply(
      fun: (DataStream[Context], FlinkCustomNodeContext) => NonEmptyList[
        (ComponentOutput, DataStream[ValueWithContext[AnyRef]])
      ]
  ): FlinkMultiOutputStreamTransformation =
    (start: DataStream[Context], context: FlinkCustomNodeContext) => fun(start, context)

}

/**
  * Every output has the same shape: each element carries a [[Context]] plus the value that becomes the node's output
  * variable there. An output with nothing to say emits `ValueWithContext(null, ctx)`, so the variable is in scope
  * downstream holding null rather than being absent. The compiler types every output with the node's declared output
  * context, so a carried context inconsistent with it (e.g. with a variable dropped) goes undiagnosed until runtime.
  *
  * `transform` returns all the node's streams as one list keyed by output; job registration looks them up by key, so
  * their order does not matter. The main output always needs exactly one stream, wired or not; an additional output
  * needs one only when the scenario connects it, so the unconnected ones may be returned or omitted. A missing or
  * duplicated key fails registration loudly. A wrong one does not: the keys are the only signal of which stream
  * belongs to which output, so pair them deliberately - a mixed-up pairing routes records down the wrong branches
  * with no error at any stage.
  *
  * A node with only the main output implements [[FlinkCustomStreamTransformation]] instead, which needs no keys.
  */
trait FlinkMultiOutputStreamTransformation extends SupportsMultipleOutputs {

  def transform(
      start: DataStream[Context],
      context: FlinkCustomNodeContext
  ): NonEmptyList[(ComponentOutput, DataStream[ValueWithContext[AnyRef]])]

}

object FlinkCustomStreamTransformation {

  def apply(
      fun: DataStream[Context] => DataStream[ValueWithContext[AnyRef]]
  ): FlinkCustomStreamTransformation =
    apply((data, _) => fun(data))

  def apply(
      fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[AnyRef]]
  ): FlinkCustomStreamTransformation =
    (start: DataStream[Context], context: FlinkCustomNodeContext) => fun(start, context)

  def apply(
      fun: (DataStream[Context], FlinkCustomNodeContext) => DataStream[ValueWithContext[AnyRef]],
      rType: TypingResult
  ): FlinkCustomStreamTransformation with ReturningType =
    new FlinkCustomStreamTransformation with ReturningType {

      override def transform(
          start: DataStream[Context],
          context: FlinkCustomNodeContext
      ): DataStream[ValueWithContext[AnyRef]] = fun(start, context)

      override def returnType: typing.TypingResult = rType
    }

}

trait FlinkCustomStreamTransformation {

  // TODO: To be consistent with ContextTransformation should return Context
  def transform(
      start: DataStream[Context],
      context: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[AnyRef]]

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
  def transform(
      inputs: Map[String, DataStream[Context]],
      context: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[AnyRef]]

}
