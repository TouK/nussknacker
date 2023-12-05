package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingParameters, RedundantParameters}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.api.{NodeId, ParameterNaming}
import pl.touk.nussknacker.engine.api.expression.{TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.graph.expression.Expression

object Validations {

  import cats.data.ValidatedNel
  import cats.implicits._

  def validateRedundantAndMissingParameters(
      parameterDefinitions: List[Parameter],
      parameters: List[evaluatedparam.Parameter]
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
      parameterDefinitions: List[Parameter],
      parameters: List[(compiledgraph.evaluatedparam.TypedParameter, Parameter)]
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[(compiledgraph.evaluatedparam.TypedParameter, Parameter)]] = {
    val definitionsMap = parameterDefinitions.map(param => (param.name, param)).toMap
    val validationResults = for {
      param           <- parameters
      paramDefinition <- definitionsMap.get(param._1.name)
      paramValidationResult = validate(paramDefinition, param)
    } yield paramValidationResult
    validationResults.sequence.map(_ => parameters)
  }

  private def validateRedundancy(definedParamNamesSet: Set[String], usedParamNamesSet: Set[String])(
      implicit nodeId: NodeId
  ) = {
    val redundantParams = usedParamNamesSet.diff(definedParamNamesSet)
    if (redundantParams.nonEmpty) RedundantParameters(redundantParams).invalidNel[Unit] else valid(())
  }

  private def validateMissingness(definedParamNamesSet: Set[String], usedParamNamesSet: Set[String])(
      implicit nodeId: NodeId
  ) = {
    val notUsedParams = definedParamNamesSet.diff(usedParamNamesSet)
    if (notUsedParams.nonEmpty) MissingParameters(notUsedParams).invalidNel[Unit] else valid(())
  }

  def validate[T](paramDefinition: Parameter, parameter: (compiledgraph.evaluatedparam.TypedParameter, T))(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, (compiledgraph.evaluatedparam.TypedParameter, T)] = {
    validate(paramDefinition.validators, parameter._1).map((_, parameter._2))
  }

  def validate(validators: List[ParameterValidator], parameter: compiledgraph.evaluatedparam.TypedParameter)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.TypedParameter] = {
    validators
      .flatMap { validator =>
        val paramWithValueAndExpressionList = parameter.typedValue match {
          case te: TypedExpression => List((parameter.name, te.typingInfo.typingResult.valueOpt, te.expression))
          case tem: TypedExpressionMap =>
            tem.valueByKey.toList.map { case (branchName, expression) =>
              (
                ParameterNaming.getNameForBranchParameter(parameter.name, branchName),
                expression.returnType.valueOpt,
                expression.expression
              )
            }
        }
        paramWithValueAndExpressionList.map { case (name, value, expression) =>
          validator.isValid(name, Expression(expression.language, expression.original), value, None).toValidatedNel
        }
      }
      .sequence
      .map(_ => parameter)
  }

}
