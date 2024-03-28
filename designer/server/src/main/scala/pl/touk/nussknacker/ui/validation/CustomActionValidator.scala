package pl.touk.nussknacker.ui.validation

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MismatchParameter
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.api.deployment.CustomActionCommand
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

class CustomActionValidator(allowedAction: CustomActionDefinition) {

  def validateCustomActionParams(
      request: CustomActionRequest
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    implicit val nodeId: NodeId = NodeId(allowedAction.name.value)
    val customActionParams      = allowedAction.parameters

    val missingParams = checkForMissingParams(request.params, customActionParams)
    val checkedParams = validateParams(request.params, customActionParams)

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

    requestParamsMap match {
      case paramsMap if paramsMap.nonEmpty =>
        handleNonEmptyParamsRequest(paramsMap, customActionParams)
      case emptyMap if emptyMap.isEmpty =>
        handleEmptyParamsRequest(customActionParams)
    }
  }

  private def handleNonEmptyParamsRequest(
      paramsMap: Map[String, String],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    paramsMap.toList.map { case (name, expression) =>
      customActionParams.find(_.name == name) match {
        case Some(param) => toValidatedNel(param, expression, name)
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

  private def toValidatedNel(param: CustomActionParameter, expressionValue: String, parameterName: String)(
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

  private def handleEmptyParamsRequest(
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    customActionParams
      .collect { case param if param.validators.nonEmpty => (param.name, param.validators) }
      .flatMap { case (name, validators) =>
        validators.collect { case validator => validator }.map {
          _.isValid(
            paramName = ParameterName(name),
            expression = Expression.spel("None"),
            value = None,
            label = None
          )
        }
      }
      .traverse(_.toValidatedNel)
      .map(_ => ())
  }

  def validateCustomActionParams(
      command: CustomActionCommand
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    this.validateCustomActionParams(
      fromCommand(command)
    )
  }

  private def fromCommand(customActionCommand: CustomActionCommand): CustomActionRequest = {
    CustomActionRequest(
      customActionCommand.actionName,
      customActionCommand.params
    )
  }

}
