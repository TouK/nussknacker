package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
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

  //we can put e.g. "? this is [my funny name]" as node name...
  private def sanitizeBranchName(branchId: String) = branchId.toCharArray.zipWithIndex.collect {
    case (a, 0) if Character.isJavaIdentifierStart(a) => a
    case (a, 0) if !Character.isJavaIdentifierStart(a) => "_"
    case (a, _) if Character.isJavaIdentifierPart(a) => a
    case (a, _) if !Character.isJavaIdentifierPart(a) => "_"
  }.mkString

  private def findUniqueParent(contextMap: Map[String, ValidationContext])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[ValidationContext]] = {
    contextMap.values.map(_.parent).toList.distinct match {
      case Nil => Valid(None)
      case a::Nil => Valid(a)
      case more => Invalid(NonEmptyList.of(CustomNodeError(nodeId.id, s"Not consistent parent contexts: ${more}", None)))
    }

  }

  @MethodToInvoke
  def execute(@BranchParamName("key") keyByBranchId: Map[String, LazyParameter[String]],
              @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[Any]],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation =
    ContextTransformation
      .join.definedBy { contexts =>
      findUniqueParent(contexts).map { parent =>
        val newType = TypedObjectTypingResult(contexts.map {
          case (branchId, _) =>
            sanitizeBranchName(branchId) -> valueByBranchId(branchId).returnType
        } + (KeyField -> Typed[String]))
        ValidationContext(Map(variableName -> newType), Map.empty, parent)
      }
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
                    sanitizeBranchName(branchId) -> evaluateValue(context)
                  ).asJava, context)
                }
              })
          }
          valuesWithContexts.reduce(_.connect(_).map(identity, identity))
        }
      }
    )

}
