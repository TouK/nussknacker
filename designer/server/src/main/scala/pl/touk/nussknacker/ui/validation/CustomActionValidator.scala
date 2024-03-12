package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.api.deployment.{CustomActionCommand, ScenarioActionName}
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, User}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

class CustomActionValidator(val allowedActions: List[CustomActionDefinition]) {

  // TODO: maybe pass around List[ParameterValidationError] in order to provide all information about the errors.
  // Currently we just throw the first that occurs.
  def validateCustomActionParams(request: CustomActionRequest): Unit = {

    val customActionOpt     = allowedActions.find(_.name == request.actionName)
    val checkedCustomAction = existsOrFail(customActionOpt, CustomActionNonExisting(request.actionName))

    implicit val nodeId: NodeId = NodeId(checkedCustomAction.name.value)
    val customActionParams      = checkedCustomAction.parameters

    val requestParamsMap = (customActionParams.nonEmpty, request.params) match {
      case (true, Some(paramsMap)) =>
        if (paramsMap.keys.size != customActionParams.size) {
          throw ValidationError(
            s"Different count of custom action parameters than provided in request for: ${request.actionName}"
          )
        }
        paramsMap
      case (true, None) =>
        throw ValidationError(s"No params defined for action: ${request.actionName}")
      case (false, Some(_)) =>
        throw ValidationError(s"Params found for no params action: ${request.actionName}")
      case _ => Map.empty[String, String]
    }

    requestParamsMap.foreach { case (k, v) =>
      customActionParams.find(_.name == k) match {
        case Some(param) =>
          param.validators.foreach { validators =>
            if (validators.nonEmpty) {
              validators.foreach(
                _.isValid(paramName = k, expression = Expression.spel("None"), value = Some(v), label = None)
                  .fold(
                    error => throw ValidationError(error.toString),
                    _ => ()
                  )
              )
            }
          }
        case None =>
          throw ValidationError("No such parameter should be defined for this action: " + checkedCustomAction.name)
      }
    }
  }

  def validateCustomActionParams(command: CustomActionCommand): Unit = {
    this.validateCustomActionParams(
      fromCommand(command)
    )
  }

  private def fromCommand(customActionCommand: CustomActionCommand): CustomActionRequest = {
    CustomActionRequest(
      customActionCommand.actionName,
      Some(customActionCommand.params)
    )
  }

  private def existsOrFail[T](checkThisOpt: Option[T], failWith: Exception): T = {
    checkThisOpt match {
      case Some(checked) => checked
      case None          => throw failWith
    }
  }

}

case class ValidationError(message: String) extends BadRequestError(message)

case class CustomActionNonExisting(actionName: ScenarioActionName) extends NotFoundError(s"$actionName is not existing")
