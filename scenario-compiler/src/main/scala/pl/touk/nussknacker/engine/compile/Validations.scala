package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{CompileTimeValidator, Parameter, ParameterValidator, Validator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.expression.parse.{MultipleBranchesTypedValue, SingleBranchTypedValue}
import pl.touk.nussknacker.engine.graph.expression.Expression

object Validations {

  import cats.data.ValidatedNel
  import cats.implicits._

  def validateWithCustomValidators(
      parameters: List[(TypedParameter, Parameter)],
      paramValidatorsMap: Map[ParameterName, ValidatedNel[PartSubGraphCompilationError, List[Validator]]]
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    parameters
      .map { case (typedParam, _) =>
        paramValidatorsMap(typedParam.name).andThen(validator => validate(validator, typedParam))
      }
      .sequence
      .void

  def validate(validators: List[Validator], parameter: TypedParameter)(
      implicit nodeId: NodeId
  ): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] = {
    val paramWithValueAndExpressionList = parameter.typedValue match {
      case single: SingleBranchTypedValue =>
        List(
          (parameter.name, single.typedExpression.typingInfo.typingResult.valueOpt, single.typedExpression.expression)
        )
      case multiple: MultipleBranchesTypedValue =>
        multiple.valueByBranchId.toList.map { case (branchName, expression) =>
          (
            parameter.name.withBranchId(branchName),
            expression.typedExpression.returnType.valueOpt,
            expression.typedExpression.expression
          )
        }
    }

    ParameterValidator
      .resolveLoaders(validators)
      .collect { case v: CompileTimeValidator => v }
      .flatMap { validator =>
        paramWithValueAndExpressionList.map { case (name, value, expression) =>
          validator.isValid(name, Expression(expression.language, expression.original), value, None).toValidatedNel
        }
      }
      .sequence
      .void
  }

}
