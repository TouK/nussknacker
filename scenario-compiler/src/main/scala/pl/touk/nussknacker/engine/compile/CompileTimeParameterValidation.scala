package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.{
  BranchEagerParameterEvaluationResult,
  EagerParameterEvaluationResult,
  NodeId,
  SingleEagerParameterEvaluationResult
}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.{CompileTimeValidator, Parameter, ParameterValidator, Validator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.expression.parse.{MultipleBranchesTypedValue, SingleBranchTypedValue}
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.jdk.OptionConverters._

object CompileTimeParameterValidation {

  def validateWithCustomValidators(
      parameters: List[(TypedParameter, Parameter)],
      paramValidatorsMap: Map[ParameterName, ValidatedNel[PartSubGraphCompilationError, List[Validator]]],
      evaluatedParamsResults: Map[ParameterName, EagerParameterEvaluationResult]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] =
    parameters
      .map { case (typedParam, _) =>
        paramValidatorsMap(typedParam.name)
          .andThen(validator => validate(validator, typedParam, evaluatedParamsResults.get(typedParam.name)))
      }
      .sequence
      .void

  def validate(
      validators: List[Validator],
      parameter: TypedParameter,
      evaluatedResult: Option[EagerParameterEvaluationResult]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    val paramWithValueAndExpressionValidated = (parameter.typedValue, evaluatedResult) match {
      case (single: SingleBranchTypedValue, None) =>
        singleParamWithValueAndExpression(parameter.name, single, evaluatedResultOpt = None)
      case (single: SingleBranchTypedValue, Some(singleResult: SingleEagerParameterEvaluationResult)) =>
        singleParamWithValueAndExpression(parameter.name, single, Some(singleResult))
      case (multiple: MultipleBranchesTypedValue, None) =>
        branchParamsWithValueAndExpression(parameter.name, multiple, evaluatedResultOpt = None)
      case (multiple: MultipleBranchesTypedValue, Some(branchResult: BranchEagerParameterEvaluationResult)) =>
        branchParamsWithValueAndExpression(parameter.name, multiple, Some(branchResult))
      case (typedValue, resultOpt) => // should never happen
        evaluationResultMismatchError(
          parameter.name,
          s"Evaluation result [$resultOpt] does not match parameter shape [$typedValue]"
        )
    }

    paramWithValueAndExpressionValidated.andThen { paramWithValueAndExpression =>
      ParameterValidator
        .resolveLoaders(validators)
        .collect { case v: CompileTimeValidator => v }
        .flatMap { validator =>
          paramWithValueAndExpression.map { param =>
            validator
              .isValid(param.name, param.expression, param.valueOpt, None)
              .toValidatedNel
          }
        }
        .sequence
        .void
    }
  }

  private def singleParamWithValueAndExpression(
      paramName: ParameterName,
      single: SingleBranchTypedValue,
      evaluatedResultOpt: Option[SingleEagerParameterEvaluationResult]
  ): ValidatedNel[PartSubGraphCompilationError, List[ParamWithValueAndExpression]] = {
    val valueOpt = evaluatedResultOpt
      .map(result => normalizeOptionalValue(result.value))
      .getOrElse(single.typedExpression.returnType.valueOpt)
    List(ParamWithValueAndExpression(paramName, single.typedExpression.expression.toExpression, valueOpt)).validNel
  }

  private def branchParamsWithValueAndExpression(
      paramName: ParameterName,
      multiple: MultipleBranchesTypedValue,
      evaluatedResultOpt: Option[BranchEagerParameterEvaluationResult]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, List[ParamWithValueAndExpression]] =
    multiple.valueByBranchId.toList.map { case (branchId, branchValue) =>
      val valueOptValidated = evaluatedResultOpt
        .map { result =>
          result.valueByBranchId
            .get(branchId)
            .map(evaluatedValue => normalizeOptionalValue(evaluatedValue).validNel)
            .getOrElse(
              evaluationResultMismatchError(paramName, s"No evaluated value for branch [$branchId]")
            )
        }
        .getOrElse(branchValue.typedExpression.returnType.valueOpt.validNel)

      valueOptValidated.map { valueOpt =>
        ParamWithValueAndExpression(
          paramName.withBranchId(branchId),
          branchValue.typedExpression.expression.toExpression,
          valueOpt
        )
      }
    }.sequence

  private def evaluationResultMismatchError(
      paramName: ParameterName,
      message: String
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Nothing] =
    CustomParameterValidationError(
      message = message,
      description = "Internal error: parameter evaluation result does not match the parameter definition",
      paramName = paramName,
      nodeId = nodeId.id
    ).invalidNel

  /**
   * Unwraps the value of an optional param (scala Option / java Optional) into the outer `Option` passed to
   * validators: an empty optional becomes `None`, and value-based validators treat a missing value as valid (there
   * is nothing to check). A non-optional value is kept (including `Some(null)`, so validators like NotNullValidator
   * still fire).
   */
  private def normalizeOptionalValue(value: Any): Option[Any] = value match {
    case option: Option[_]               => option
    case optional: java.util.Optional[_] => optional.toScala
    case other                           => Some(other)
  }

  private final case class ParamWithValueAndExpression(
      name: ParameterName,
      expression: Expression,
      valueOpt: Option[Any]
  )

}
