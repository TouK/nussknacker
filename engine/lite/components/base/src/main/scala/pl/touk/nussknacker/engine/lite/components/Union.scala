package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.Validated
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, NodeId}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.{BranchParamName, CustomStreamTransformer, LazyParameter, MethodToInvoke, OutputVariableName}
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, JoinDataBatch, LiteJoinCustomComponent}

import scala.language.higherKinds

//TODO: unify definition with UnionTransformer
object Union extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(@BranchParamName("Output expression") outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation = {
    ContextTransformation
      .join
      .definedBy { contexts =>
        val branchReturnTypes: Iterable[typing.TypingResult] = outputExpressionByBranchId.values.map(_.returnType)
        ContextTransformation.findUniqueParentContext(contexts).map { parent =>
          ValidationContext(Map(variableName -> branchReturnTypes.head), Map.empty, parent)
        }.andThen { vc =>
          Validated.cond(branchReturnTypes.toSet.size == 1, vc, CannotCreateObjectError("All branch values must be of the same type", nodeId.id)).toValidatedNel
        }

      }
      .implementedBy(new LiteJoinCustomComponent {
        override def createTransformation[F[_] : Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): JoinDataBatch => F[ResultType[Result]] = {
          (inputs: JoinDataBatch) => {
            val contextWithNewValue = inputs.value.map {
              case (branchId, branchContext) =>
                val branchOutputExpression = outputExpressionByBranchId(branchId.value)
                val interpreter = context.interpreter.syncInterpretationFunction(branchOutputExpression)
                val branchNewValue = interpreter(branchContext)
                branchContext.withVariable(variableName, branchNewValue)
            }
            continuation(DataBatch(contextWithNewValue))
          }
        }
      })
  }

  override def canHaveManyInputs: Boolean = true

}
