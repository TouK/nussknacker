package pl.touk.nussknacker.engine.compile

import cats.data.Validated.valid
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, RedundantParameters}
import pl.touk.nussknacker.engine.api.definition.{Parameter, Validator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.expression.parse.{TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression

object Validations {

  import cats.data.ValidatedNel
  import cats.implicits._

  def validateRedundantAndMissingParameters(
      parameterDefinitions: List[Parameter],
      parameters: List[NodeParameter]
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    val definedParamNamesSet = parameterDefinitions.map(_.name).toSet
    val usedParamNamesSet    = parameters.map(_.name).toSet

    val validatedRedundant = validateRedundancy(definedParamNamesSet, usedParamNamesSet)
    val validatedMissing   = validateMissingness(definedParamNamesSet, usedParamNamesSet)

    validatedRedundant.combine(validatedMissing)
  }

  def validateWithCustomValidators(
      parameters: List[(TypedParameter, Parameter)],
      paramValidatorsMap: Map[ParameterName, ValidatedNel[PartSubGraphCompilationError, List[Validator]]]
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[(TypedParameter, Parameter)]] =
    parameters
      .map { case (typedParam, _) =>
        paramValidatorsMap(typedParam.name).andThen(validator => validate(validator, typedParam))
      }
      .sequence
      .map(_ => parameters)

  private def validateRedundancy(definedParamNamesSet: Set[ParameterName], usedParamNamesSet: Set[ParameterName])(
      implicit nodeId: NodeId
  ) = {
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    if (redundantParams.nonEmpty) RedundantParameters(redundantParams).invalidNel[Unit] else valid(())
  }

  private def validateMissingness(definedParamNamesSet: Set[ParameterName], usedParamNamesSet: Set[ParameterName])(
      implicit nodeId: NodeId
  ) = {
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    if (notUsedParams.nonEmpty) MissingParameters(notUsedParams).invalidNel[Unit] else valid(())
  }

  def validate[T](validators: List[Validator], parameter: (TypedParameter, T))(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, (TypedParameter, T)] = {
    validate(validators, parameter._1).map((_, parameter._2))
  }

  def validate(validators: List[Validator], parameter: TypedParameter)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    val paramWithValueAndExpressionList = parameter.typedValue match {
      case te: TypedExpression => List((parameter.name, te.typingInfo.typingResult.valueOpt, te.expression))
      case tem: TypedExpressionMap =>
        tem.valueByKey.toList.map { case (branchName, expression) =>
          (
            parameter.name.withBranchId(branchName),
            expression.returnType.valueOpt,
            expression.expression
          )
        }
    }

    validators
      .flatMap { validator =>
        paramWithValueAndExpressionList.map { case (name, value, expression) =>
          validator.isValid(name, Expression(expression.language, expression.original), value, None).toValidatedNel
        }
      }
      .sequence
      .map(_ => parameter)
  }

}
