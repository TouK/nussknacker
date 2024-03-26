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

class CustomActionValidator(allowedAction: CustomActionDefinition) {

  def validateCustomActionParams(
      request: CustomActionRequest
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {

    implicit val nodeId: NodeId = NodeId(allowedAction.name.value)
    val customActionParams      = allowedAction.parameters

    validateParams(request.params, customActionParams)
  }

  private def validateParams(
      requestParamsMap: Option[Map[String, String]],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId) = {

    requestParamsMap match {
      case Some(paramsMap) =>
        handleNonEmptyParamsRequest(paramsMap, customActionParams)
      case None =>
        handleEmptyParamsRequest(customActionParams)

    }
  }

  private def handleNonEmptyParamsRequest(
      paramsMap: Map[String, String],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    paramsMap.toList.map { case (k, v) =>
      customActionParams.find(_.name == k) match {
        case Some(param) => toValidatedNel(param, v, k)
        case None =>
          MismatchParameter(
            s"Couldn't find a matching parameter in action definition for this param: $k",
            "",
            ParameterName(k),
            nodeId.id
          ).invalidNel[Unit]
      }
    }.sequence_
  }

  private def toValidatedNel(param: CustomActionParameter, v: String, k: String)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    param.validators
      .getOrElse(Nil)
      .map { validator =>
        validator.isValid(
          paramName = ParameterName(k),
          expression = Expression.spel(v),
          value = Some(v),
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
      .collect { case param if param.validators.nonEmpty => (param.name, param.validators.get) }
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
    val checkedParams = customActionCommand.params match {
      case empty if empty.isEmpty => None
      case full if full.nonEmpty  => Some(full)
    }

    CustomActionRequest(
      customActionCommand.actionName,
      checkedParams
    )
  }

}
