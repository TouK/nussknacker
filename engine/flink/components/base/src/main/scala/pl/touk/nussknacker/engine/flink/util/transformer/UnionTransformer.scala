package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.co.CoMapFunction
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.context.{
  ContextTransformation,
  JoinContextTransformation,
  ProcessCompilationError,
  ValidationContext
}
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{
  AbstractLazyParameterInterpreterFunction,
  FlinkCustomJoinTransformation,
  FlinkCustomNodeContext
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.timestamp.TimestampAssignmentHelper

object UnionTransformer extends UnionTransformer(None) {

  def transformContextsDefinition(outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]], variableName: String)(
      contexts: Map[String, ValidationContext]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val branchReturnTypes = outputExpressionByBranchId.values.map(_.returnType)
    val unifiedReturnType = NonEmptyList.fromList(branchReturnTypes.toList).flatMap { case NonEmptyList(head, tail) =>
      tail.foldLeft(Option(head)) { (acc, el) =>
        acc.flatMap(findSuperTypeCheckingAllFieldsMatchingForObjects(_, el))
      }
    }
    unifiedReturnType
      .map(unionValidationContext(variableName, contexts, _))
      .getOrElse(Validated.invalidNel(CannotCreateObjectError("All branch values must be of the same type", nodeId.id)))
  }

  private def findSuperTypeCheckingAllFieldsMatchingForObjects(
      left: TypingResult,
      right: TypingResult
  ): Option[TypingResult] = {
    CommonSupertypeFinder.Intersection
      .commonSupertypeOpt(left, right)
      .flatMap { result =>
        (left, right, result) match {
          // normally (e.g. in equals) we are more lax in comparison of objects, but here we want to strictly check
          // if all fields are similar (has common super type) - it is kind of replacement for nice gui editor showing those fields are equal
          case (leftObj: TypedObjectTypingResult, rightObj: TypedObjectTypingResult, resultObj: TypedObjectTypingResult)
              if resultObj.fields.keySet != leftObj.fields.keySet || resultObj.fields.keySet != rightObj.fields.keySet =>
            None
          case _ =>
            Some(result)
        }
      }
  }

  private def unionValidationContext(
      variableName: String,
      contexts: Map[String, ValidationContext],
      branchReturnType: TypingResult
  )(implicit nodeId: NodeId) = {
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
    extends CustomStreamTransformer
    with LazyLogging
    with Serializable {

  import UnionTransformer._

  @MethodToInvoke
  def execute(
      @BranchParamName("Output expression") outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]],
      @OutputVariableName variableName: String
  )(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation.join
      .definedBy(transformContextsDefinition(outputExpressionByBranchId, variableName)(_))
      .implementedBy(
        new FlinkCustomJoinTransformation {

          override def transform(
              inputs: Map[String, DataStream[Context]],
              context: FlinkCustomNodeContext
          ): DataStream[ValueWithContext[AnyRef]] = {
            val valuesWithContexts = inputs.map { case (branchId, stream) =>
              val valueParam = outputExpressionByBranchId(branchId)
              stream.flatMap(new UnionMapFunction(valueParam, context, branchId))
            }
            val connectedStream = valuesWithContexts.reduce(
              _.connect(_).map(
                new CoMapFunction[ValueWithContext[AnyRef], ValueWithContext[AnyRef], ValueWithContext[AnyRef]] {
                  override def map1(value: ValueWithContext[AnyRef]): ValueWithContext[AnyRef] = value
                  override def map2(value: ValueWithContext[AnyRef]): ValueWithContext[AnyRef] = value
                }
              )
            )

            timestampAssigner
              .map(
                new TimestampAssignmentHelper[ValueWithContext[AnyRef]](_)(context.valueWithContextInfo.forUnknown)
                  .assignWatermarks(connectedStream)
              )
              .getOrElse(connectedStream)
          }

        }
      )

}

class UnionMapFunction(valueParam: LazyParameter[AnyRef], customNodeContext: FlinkCustomNodeContext, branchId: String)
    extends AbstractLazyParameterInterpreterFunction(customNodeContext.lazyParameterHelper)
    with FlatMapFunction[Context, ValueWithContext[AnyRef]] {

  private lazy val evaluateValue = toEvaluateFunctionConverter.toEvaluateFunction(valueParam)

  override def flatMap(context: Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val unionContext = context.appendIdSuffix(branchId)
    collectHandlingErrors(unionContext, out) {
      ValueWithContext[AnyRef](evaluateValue(unionContext), unionContext)
    }
  }

}
