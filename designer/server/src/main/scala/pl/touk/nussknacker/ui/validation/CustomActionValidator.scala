package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.api.{NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, User}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.process.deployment.ValidationError

class CustomActionValidator(val allowedActions: List[CustomActionDefinition]) {

  private def existsOrFail[T](checkThisOpt: Option[T], failWith: Exception): T = {
    checkThisOpt match {
      case Some(checked) => checked
      case None          => throw failWith
    }
  }

  def validateCustomActionParams(request: pl.touk.nussknacker.engine.deployment.CustomActionRequest): Unit = {

    val customActionOpt     = allowedActions.find(_.name == request.name)
    val checkedCustomAction = existsOrFail(customActionOpt, new IllegalArgumentException("actionReq.name"))

    implicit val nodeId: NodeId = NodeId(checkedCustomAction.name.value)
    val requestParamsMap        = request.params
    val customActionParams      = checkedCustomAction.parameters

    if (requestParamsMap.keys.size != customActionParams.size) {
      throw ValidationError("Different count of custom action parameters than provided in request for: " + request.name)
    }

    requestParamsMap.foreach { case (k, v) =>
      customActionParams.find(_.name == k) match {
        case Some(param) =>
          param.validators.foreach { validators =>
            if (validators.nonEmpty) {
              validators.foreach(
                _.isValid(paramName = k, expression = Expression.spel("None"), value = Some(v), label = None)
              )
            }
          }
        case None =>
          throw ValidationError("No such parameter should be defined for this action: " + checkedCustomAction.name)
      }
    }
  }

  def validateCustomActionParams(dtoRequest: pl.touk.nussknacker.restmodel.CustomActionRequest): Unit = {
    this.validateCustomActionParams(
      pl.touk.nussknacker.engine.deployment.CustomActionRequest(
        dtoRequest.actionName,
        ProcessVersion.empty,
        User("empty", "empty"),
        dtoRequest.params.getOrElse(ValidationError)
      )
    )
  }

}
