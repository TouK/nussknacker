package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.Validated
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, NodeId}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.supertype.{ClassHierarchyCommonSupertypeFinder, CommonSupertypeFinder, NumberTypesPromotionStrategy, SupertypeClassResolutionStrategy}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{BranchParamName, Context, CustomStreamTransformer, LazyParameter, MethodToInvoke, OutputVariableName, VariableConstants}
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CustomComponentContext, JoinDataBatch, LiteJoinCustomComponent}

import scala.language.higherKinds

//TODO: unify definition with UnionTransformer
object Union extends CustomStreamTransformer {

  private val superTypeFinder = new CommonSupertypeFinder(SupertypeClassResolutionStrategy.Intersection, true)

  @MethodToInvoke
  def execute(@BranchParamName("Output expression") outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): JoinContextTransformation = {
    ContextTransformation
      .join
      .definedBy { contexts =>
        val branchReturnTypes: List[typing.TypingResult] = outputExpressionByBranchId.values.map(_.returnType).toList
        val unifiedReturnType = branchReturnTypes match {
          case Nil => None
          case one :: Nil => Some(one)
          case twoOrMore => Some(twoOrMore.reduce(superTypeFinder.commonSupertype(_, _)(NumberTypesPromotionStrategy.ToSupertype))).filterNot(_ == Typed.empty)
        }
        unifiedReturnType
          .map(unionValidationContext(variableName, contexts, _))
          .getOrElse(Validated.invalidNel(CannotCreateObjectError("All branch values must be of the same type", nodeId.id)))
      }
      .implementedBy(new LiteJoinCustomComponent {
        override def createTransformation[F[_] : Monad, Result](continuation: DataBatch => F[ResultType[Result]], context: CustomComponentContext[F]): JoinDataBatch => F[ResultType[Result]] = {
          val interpreterByBranchId = outputExpressionByBranchId.mapValues(context.interpreter.syncInterpretationFunction)
          (inputs: JoinDataBatch) => {
            val contextWithNewValue = inputs.value.map {
              case (branchId, branchContext) =>
                val branchNewValue = interpreterByBranchId(branchId.value)(branchContext)
                branchContext
                  .clearUserVariables
                  .withVariable(variableName, branchNewValue)
            }
            continuation(DataBatch(contextWithNewValue))
          }
        }
      })
  }

  private def unionValidationContext(variableName: String, contexts: Map[String, ValidationContext], branchReturnType: TypingResult)(implicit nodeId: NodeId) = {
    ContextTransformation.findUniqueParentContext(contexts).map { parent =>
      ValidationContext(Map(variableName -> branchReturnType), Map.empty, parent)
    }
  }

  override def canHaveManyInputs: Boolean = true

}
