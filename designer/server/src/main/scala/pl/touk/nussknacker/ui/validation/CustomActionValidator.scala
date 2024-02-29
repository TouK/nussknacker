package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.api.deployment.CustomActionCommand
import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, User}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.ui.process.deployment.ValidationError

class CustomActionValidator(val allowedActions: List[CustomActionDefinition]) {

  def validateCustomActionParams(request: CustomActionRequest): Unit = {

    val customActionOpt     = allowedActions.find(_.name == request.actionName)
    val checkedCustomAction = existsOrFail(customActionOpt, new IllegalArgumentException("actionReq.name"))

    implicit val nodeId: NodeId = NodeId(checkedCustomAction.name.value)
    val requestParamsMap =
      request.params.getOrElse(throw ValidationError("No params defined for action: " + request.actionName))
    val customActionParams = checkedCustomAction.parameters

    if (requestParamsMap.keys.size != customActionParams.size) {
      throw ValidationError(
        "Different count of custom action parameters than provided in request for: " + request.actionName
      )
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
