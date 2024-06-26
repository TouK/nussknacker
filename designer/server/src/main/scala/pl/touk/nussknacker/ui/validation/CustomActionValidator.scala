package pl.touk.nussknacker.ui.validation

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MismatchParameter
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression

//TODO: consider unifying this with ScenarioPropertiesValidator as the logic is very similar (possibly identical)
class CustomActionValidator(actionDefinition: CustomActionDefinition) {

  def validateCustomActionParams(
      params: Map[String, String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    implicit val nodeId: NodeId = NodeId(actionDefinition.actionName.value)

    val missingParams = checkForMissingParams(params, actionDefinition.parameters)
    val checkedParams = validateParams(params, actionDefinition.parameters)

    missingParams.combine(checkedParams)
  }

  private def checkForMissingParams(
      requestParamsMap: Map[String, String],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    val paramsFromRequest = requestParamsMap.keys.toSet
    val mandatoryParams = customActionParams.collect {
      case param if param.validators.contains(MandatoryParameterValidator) && !paramsFromRequest.contains(param.name) =>
        MandatoryParameterValidator.isValid(ParameterName(param.name), Expression.spel(""), None, None)
    }
    mandatoryParams match {
      case Nil      => Validated.Valid(())
      case nonEmpty => nonEmpty.traverse(_.toValidatedNel).map(_ => ())
    }
  }

  private def validateParams(
      requestParamsMap: Map[String, String],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    requestParamsMap.toList.map { case (name, expression) =>
      customActionParams.find(_.name == name) match {
        case Some(param) => validate(param, expression, name)
        case None =>
          MismatchParameter(
            s"Couldn't find a matching parameter in action definition for this param: $name",
            "",
            ParameterName(name),
            nodeId.id
          ).invalidNel[Unit]
      }
    }.sequence_
  }

  private def validate(param: CustomActionParameter, expressionValue: String, parameterName: String)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    param.validators
      .map { validator =>
        validator.isValid(
          paramName = ParameterName(parameterName),
          expression = Expression.spel(expressionValue),
          value = Some(expressionValue),
          label = None
        )
      }
      .traverse(_.toValidatedNel)
      .map(_ => ())
  }

}
