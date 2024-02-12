package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionRequest}
import pl.touk.nussknacker.engine.graph.expression.Expression

object CustomActionValidator {

  def validateCustomActionParams(request: CustomActionRequest, customAction: CustomActionDefinition): Unit = {
    implicit val nodeId: NodeId = NodeId(customAction.name.value)
    val requestParamsMap        = request.params
    val customActionParams      = customAction.parameters

    if (requestParamsMap.keys.size != customActionParams.size) {
      throw new Exception("Different count of custom action parameters than provided in request for: " + request.name)
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
        case None => throw new Exception("No such parameter should be defined for this action: " + customAction.name)
      }
    }
  }

}
