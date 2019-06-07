package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, AbstractOneParamLazyParameterFunction, FlinkCustomJoinTransformation, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import scala.collection.JavaConverters._

/**
  * It creates union of joined data streams. Produced variable will be a map which looks like:
  * ```
  * {
  *   key: ${result of evaluation of key expression for branch1}
  *   branchId: ${result of evaluation of value expression for branchId}
  * }
  * ```
  * `branchId` field of map will have Unknown type. If you want to specify it, you can pass type
  * as a Map in `definition` parameter.
  */
case object UnionTransformer extends CustomStreamTransformer with LazyLogging {

  private val KeyField = "key"

  override def canHaveManyInputs: Boolean = true

  @MethodToInvoke
  def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[Any]],
              @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[Any]],
              @ParamName("type") definition: java.util.Map[String, _],
              @OutputVariableName variableName: String): JoinContextTransformation =
    ContextTransformation
      .join.definedBy { contexts =>
      // TODO: remove definition parameter when valueByBranchId(branchId).returnType will produce correct type
      val newType = if (definition == null) {
        TypedObjectTypingResult(contexts.toSeq.map {
          case (branchId, _) =>
            branchId -> valueByBranchId(branchId).returnType
        }.toMap + (KeyField -> Unknown))
      } else {
        TypingUtils.typeMapDefinition(definition.asScala.toMap + (KeyField -> Unknown))
      }
      Valid(ValidationContext(Map(variableName -> newType)))
    }.implementedBy(
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]] = {
          val valuesWithContexts = inputs.map {
            case (branchId, stream) =>
              stream
                .map(new AbstractLazyParameterInterpreterFunction(context.lazyParameterHelper)
                  with MapFunction[Context, ValueWithContext[Any]] {

                  private lazy val evaluateKey = lazyParameterInterpreter.syncInterpretationFunction(keyByBranchId(branchId))

                  private lazy val evaluateValue = lazyParameterInterpreter.syncInterpretationFunction(valueByBranchId(branchId))

                  override def map(context: Context): ValueWithContext[Any] = {
                    ValueWithContext(Map(
                      KeyField -> evaluateKey(context),
                      branchId -> evaluateValue(context)
                    ), context)
                  }

                })
          }
          valuesWithContexts.reduce(_.connect(_).map(identity, identity))
        }
      }
    )

}
