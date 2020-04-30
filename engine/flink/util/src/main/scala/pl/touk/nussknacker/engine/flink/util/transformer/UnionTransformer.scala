package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, FlinkCustomJoinTransformation, FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper

case object UnionTransformer extends UnionTransformer(None) {

  val KeyField = "key"

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
class UnionTransformer(timestampAssigner: Option[TimestampAssigner[TimestampedValue[ValueWithContext[Any]]]])
  extends CustomStreamTransformer with LazyLogging {

  override def canHaveManyInputs: Boolean = true

  //we can put e.g. "? this is [my funny name]" as node name...
  private def sanitizeBranchName(branchId: String) = branchId.toCharArray.zipWithIndex.collect {
    case (a, 0) if Character.isJavaIdentifierStart(a) => a
    case (a, 0) if !Character.isJavaIdentifierStart(a) => "_"
    case (a, _) if Character.isJavaIdentifierPart(a) => a
    case (a, _) if !Character.isJavaIdentifierPart(a) => "_"
  }.mkString
  
  @MethodToInvoke
  def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[CharSequence]],
              @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[Any]],
              @OutputVariableName variableName: String): JoinContextTransformation =
    ContextTransformation
      .join.definedBy { contexts =>
      val newType = TypedObjectTypingResult(contexts.map {
          case (branchId, _) =>
            sanitizeBranchName(branchId) -> valueByBranchId(branchId).returnType
        } + (UnionTransformer.KeyField -> Typed[String]))

      Valid(ValidationContext(Map(variableName -> newType)))
    }.implementedBy(
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]] = {
          val valuesWithContexts = inputs.map {
            case (branchId, stream) =>
              val keyParam = keyByBranchId(branchId)
              val valueParam = valueByBranchId(branchId)
              stream.map(new UnionMapFunction(sanitizeBranchName(branchId), keyParam, valueParam, context.lazyParameterHelper))
          }
          val connectedStream = valuesWithContexts.reduce(_.connect(_).map(identity, identity))

          timestampAssigner
            .map(new TimestampAssignmentHelper[ValueWithContext[Any]](_).assignWatermarks(connectedStream))
            .getOrElse(connectedStream)
        }
      }
    )

}

class UnionMapFunction(valueField: String,
                       keyParam: LazyParameter[CharSequence], valueParam: LazyParameter[Any],
                       lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends AbstractLazyParameterInterpreterFunction(lazyParameterHelper) with MapFunction[Context, ValueWithContext[Any]] {

  private lazy val evaluateKey =  lazyParameterInterpreter.syncInterpretationFunction(keyParam)
  private lazy val evaluateValue = lazyParameterInterpreter.syncInterpretationFunction(valueParam)

  override def map(context: Context): ValueWithContext[Any] = {
    import scala.collection.JavaConverters._
    ValueWithContext(Map(
      UnionTransformer.KeyField -> Option(evaluateKey(context)).map(_.toString).orNull,
      valueField -> evaluateValue(context)
    ).asJava, context)
  }

}
