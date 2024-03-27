package pl.touk.nussknacker.ui.validation

import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MismatchParameter
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

    validateParams(request.params, customActionParams)
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

//GSK: Too much happens here. This error was treated as an indication group of errors and an error from this group.
// Aaand BadRequestError is from old akka ManagementResources approach.
case class CustomActionValidationError(message: String) extends BadRequestError(message)
//GSK: this is exception in the scope of old akka ManagementResources
case class CustomActionNonExistingError(message: String) extends NotFoundError(message)
