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
import pl.touk.nussknacker.engine.api.typed.supertype.{CommonSupertypeFinder, NumberTypesPromotionStrategy, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper

case object UnionTransformer extends UnionTransformer(None) {

  private val superTypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Intersection, true)

  def transformContextsDefinition(outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)
                                 (contexts: Map[String, ValidationContext])
                                 (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val branchReturnTypes: List[typing.TypingResult] = outputExpressionByBranchId.values.map(_.returnType).toList
    val unifiedReturnType = branchReturnTypes.reduceOption[TypingResult] { case (left, right) =>
      findSuperTypeCheckingAllFieldsMatchingForObjects(left, right)
    }.filterNot(_ == Typed.empty)
    unifiedReturnType
      .map(unionValidationContext(variableName, contexts, _))
      .getOrElse(Validated.invalidNel(CannotCreateObjectError("All branch values must be of the same type", nodeId.id)))
  }

  private def findSuperTypeCheckingAllFieldsMatchingForObjects(left: TypingResult, right: TypingResult): TypingResult = {
    val result = superTypeFinder.commonSupertype(left, right)(NumberTypesPromotionStrategy.ToSupertype)
    (left, right, result) match {
      // normally (e.g. in ternary operator and equals) we are more lax in comparison of objects, but here we want to strictly check
      // if all fields are similar (has common super type) - it is kind of replacement for nice gui editor showing those fields are equal
      case (leftObj: TypedObjectTypingResult, rightObj: TypedObjectTypingResult, resultObj: TypedObjectTypingResult) if resultObj.fields.keySet != leftObj.fields.keySet || resultObj.fields.keySet != rightObj.fields.keySet =>
        Typed.empty
      case _ =>
        result
    }
  }

  private def unionValidationContext(variableName: String, contexts: Map[String, ValidationContext], branchReturnType: TypingResult)(implicit nodeId: NodeId) = {
    ContextTransformation.findUniqueParentContext(contexts).map { parent =>
      ValidationContext(Map(variableName -> branchReturnType), Map.empty, parent)
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

  val outputExpressionParameterName = "Output expression"

  @MethodToInvoke
  def execute(@BranchParamName("Output expression") outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation
      .join.definedBy(transformContextsDefinition(outputExpressionByBranchId, variableName)(_))
      .implementedBy(
        new FlinkCustomJoinTransformation {
          override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
            val valuesWithContexts = inputs.map {
              case (branchId, stream) =>
                val valueParam = outputExpressionByBranchId(branchId)
                stream.flatMap(new UnionMapFunction(valueParam, context))
            }
            val connectedStream = valuesWithContexts.reduce(_.connect(_).map(identity, identity))

            timestampAssigner
              .map(new TimestampAssignmentHelper[ValueWithContext[AnyRef]](_).assignWatermarks(connectedStream))
              .getOrElse(connectedStream)
          }
        }
      )

}

class UnionMapFunction(valueParam: LazyParameter[AnyRef],
                       customNodeContext: FlinkCustomNodeContext)
  extends AbstractLazyParameterInterpreterFunction(customNodeContext.lazyParameterHelper) with FlatMapFunction[Context, ValueWithContext[AnyRef]] {

  private lazy val evaluateValue = lazyParameterInterpreter.syncInterpretationFunction(valueParam)

  override def flatMap(context: Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    collectHandlingErrors(context, out) {
      ValueWithContext[AnyRef](evaluateValue(context), context)
    }
  }

}
