package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, FlinkCustomJoinTransformation, FlinkCustomNodeContext}

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
  */
case object UnionTransformer extends CustomStreamTransformer with LazyLogging {

  private val KeyField = "key"

  override def canHaveManyInputs: Boolean = true

  @MethodToInvoke
  def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[String]],
              @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[Any]],
              @OutputVariableName variableName: String): JoinContextTransformation =
    ContextTransformation
      .join.definedBy { contexts =>
      val newType = TypedObjectTypingResult(contexts.map {
          case (branchId, _) =>
            branchId -> valueByBranchId(branchId).returnType
        } + (KeyField -> Typed[String]))

      Valid(ValidationContext(Map(variableName -> newType)))
    }.implementedBy(
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]] = {
          val valuesWithContexts = inputs.map {
            case (branchId, stream) =>
              val keyParam = keyByBranchId(branchId)
              val valueParam = valueByBranchId(branchId)
              stream.map(new AbstractLazyParameterInterpreterFunction(context.lazyParameterHelper)
                with MapFunction[Context, ValueWithContext[Any]] {

                private lazy val evaluateKey =  lazyParameterInterpreter.syncInterpretationFunction(keyParam)
                private lazy val evaluateValue = lazyParameterInterpreter.syncInterpretationFunction(valueParam)

                override def map(context: Context): ValueWithContext[Any] = {
                  import scala.collection.JavaConverters._
                  ValueWithContext(Map(
                    KeyField -> evaluateKey(context),
                    branchId -> evaluateValue(context)
                  ).asJava, context)
                }
              })
          }
          valuesWithContexts.reduce(_.connect(_).map(identity, identity))
        }
      }
    )

}
