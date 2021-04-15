package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, FlinkCustomJoinTransformation, FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper

case object UnionTransformer extends UnionTransformer(None) {

  val KeyField = "key"

  def transformContextsDefinition(valueByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)
                                 (inputContexts: Map[String, ValidationContext])
                                 (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    ContextTransformation.findUniqueParentContext(inputContexts).map { parent =>
      val newType = TypedObjectTypingResult(
        (UnionTransformer.KeyField -> Typed[String]) :: inputContexts.map {
          case (branchId, _) =>
            ContextTransformation.sanitizeBranchName(branchId) -> valueByBranchId(branchId).returnType
        }.toList
      )
      ValidationContext(Map(variableName -> newType), Map.empty, parent)
    }
  }

}

/**
 * It creates union of joined data streams. Produced variable will be a map which looks like:
 * ```
 * {
 *   key: result_of_evaluation_of_key_expression_for_branch1
 *   branchId: result_of_evaluation_of_value_expression_for_branchId
 * }
 * ```
 * `branchId` field of map will have Unknown type. If you want to specify it, you can pass type
 * as a Map in `definition` parameter.
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
  def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]],
              @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[AnyRef]],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation
      .join.definedBy(transformContextsDefinition(valueByBranchId, variableName)(_))
      .implementedBy(
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
          val valuesWithContexts = inputs.map {
            case (branchId, stream) =>
              val keyParam = keyByBranchId(branchId)
              val valueParam = valueByBranchId(branchId)
              stream.map(new UnionMapFunction(ContextTransformation.sanitizeBranchName(branchId), keyParam, valueParam, context.lazyParameterHelper))
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
                       keyParam: LazyParameter[CharSequence], valueParam: LazyParameter[AnyRef],
                       lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends AbstractLazyParameterInterpreterFunction(lazyParameterHelper) with MapFunction[Context, ValueWithContext[AnyRef]] {

  private lazy val evaluateKey =  lazyParameterInterpreter.syncInterpretationFunction(keyParam)
  private lazy val evaluateValue = lazyParameterInterpreter.syncInterpretationFunction(valueParam)

  override def map(context: Context): ValueWithContext[AnyRef] = {
    import scala.collection.JavaConverters._
    ValueWithContext(Map(
      UnionTransformer.KeyField -> Option(evaluateKey(context)).map(_.toString).orNull,
      valueField -> evaluateValue(context)
    ).asJava, context)
  }

}
