package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.{Validated, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, NodeId}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper

case object UnionTransformer extends UnionTransformer(None) {

  def transformContextsDefinition(valueByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)
                                 (inputContexts: Map[String, ValidationContext])
                                 (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val branchReturnTypes: Iterable[typing.TypingResult] = valueByBranchId.values.map(_.returnType)
    ContextTransformation.findUniqueParentContext(inputContexts).map { parent =>
      ValidationContext(Map(variableName -> branchReturnTypes.head), Map.empty, parent)
    }.andThen { vc =>
      Validated.cond(branchReturnTypes.toSet.size == 1, vc, CannotCreateObjectError("All branch values must be of the same type", nodeId.id)).toValidatedNel
    }

  }

}

/**
  * It creates union of joined data streams. Produced variable will be of type of value expression
  *
  * @param timestampAssigner Optional timestamp assigner that will be used on connected stream.
  *                          Make notice that Flink produces min watermark(left stream watermark, right stream watermark)
  *                          for connected streams. In some cases, when you have some time-based aggregation after union,
  *                          you would like to redefine this logic.
  */
class UnionTransformer(timestampAssigner: Option[TimestampWatermarkHandler[TimestampedValue[ValueWithContext[AnyRef]]]])
  extends CustomStreamTransformer with LazyLogging {

  import UnionTransformer._

  override def canHaveManyInputs: Boolean = true

  @MethodToInvoke
  def execute(@BranchParamName("value") valueByBranchId: Map[String, LazyParameter[AnyRef]],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation
      .join.definedBy(transformContextsDefinition(valueByBranchId, variableName)(_))
      .implementedBy(
        new FlinkCustomJoinTransformation {
          override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
            val valuesWithContexts = inputs.map {
              case (branchId, stream) =>
                val valueParam = valueByBranchId(branchId)
                stream.flatMap(new UnionMapFunction(ContextTransformation.sanitizeBranchName(branchId), valueParam, context))
            }
            val connectedStream = valuesWithContexts.reduce(_.connect(_).map(identity, identity))

            timestampAssigner
              .map(new TimestampAssignmentHelper[ValueWithContext[AnyRef]](_).assignWatermarks(connectedStream))
              .getOrElse(connectedStream)
          }
        }
      )

}

class UnionMapFunction(valueField: String,
                       valueParam: LazyParameter[AnyRef],
                       customNodeContext: FlinkCustomNodeContext)
  extends AbstractLazyParameterInterpreterFunction(customNodeContext.lazyParameterHelper) with FlatMapFunction[Context, ValueWithContext[AnyRef]] {

  private lazy val evaluateValue = lazyParameterInterpreter.syncInterpretationFunction(valueParam)

  override def flatMap(context: Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    collectHandlingErrors(context, out) {
      ValueWithContext[AnyRef](valueField -> evaluateValue(context), context)
    }
  }

}
