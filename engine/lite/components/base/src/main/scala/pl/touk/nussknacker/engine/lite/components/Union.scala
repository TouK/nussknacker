package pl.touk.nussknacker.engine.lite.components

import cats.Monad
import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{
  CustomComponentContext,
  JoinDataBatch,
  LiteJoinCustomComponent
}

import scala.language.higherKinds

//TODO: unify definition with UnionTransformer
object Union extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(
      @BranchParamName("Output expression") outputExpressionByBranchId: Map[String, LazyParameter[AnyRef]],
      @OutputVariableName variableName: String
  )(implicit nodeId: NodeId): JoinContextTransformation = {
    ContextTransformation.join
      .definedBy { contexts =>
        val branchReturnTypes = outputExpressionByBranchId.values.map(_.returnType)
        val unifiedReturnType =
          NonEmptyList.fromList(branchReturnTypes.toList).flatMap { case NonEmptyList(head, tail) =>
            tail.foldLeft(Option(head)) { (acc, el) =>
              acc.flatMap(findSuperTypeCheckingAllFieldsMatchingForObjects(_, el))
            }
          }
        unifiedReturnType
          .map(unionValidationContext(variableName, contexts, _))
          .getOrElse(
            Validated.invalidNel(CannotCreateObjectError("All branch values must be of the same type", nodeId.id))
          )
      }
      .implementedBy(new LiteJoinCustomComponent {
        override def createTransformation[F[_]: Monad, Result](
            continuation: DataBatch => F[ResultType[Result]],
            context: CustomComponentContext[F]
        ): JoinDataBatch => F[ResultType[Result]] = { (inputs: JoinDataBatch) =>
          {
            val contextWithNewValue = inputs.value.map { case (branchId, branchContext) =>
              val branchNewValue = outputExpressionByBranchId(branchId.value).evaluate(branchContext)
              branchContext.clearUserVariables
                .withVariable(variableName, branchNewValue)
                .appendIdSuffix(branchId.value)
            }
            continuation(DataBatch(contextWithNewValue))
          }
        }
      })
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
