package pl.touk.nussknacker.ui.validation

import cats.data.Validated.Invalid
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.deployment.{CustomActionCommand, ScenarioActionName}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.deployment.{
  CustomActionDefinition,
  CustomActionParameter,
  CustomActionValidationResult
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError, NuDesignerError}

class CustomActionValidator(val allowedActions: List[CustomActionDefinition]) {

  def validateCustomActionParams(
      request: CustomActionRequest
  ): Either[NuDesignerError, CustomActionValidationResult] = {

    val checkedCustomAction =
      allowedActions.find(_.name == request.actionName).toRight(CustomActionNonExisting(request.actionName))

    checkedCustomAction match {
      case Left(notFoundAction) => Left(notFoundAction)

      case Right(foundAction) => {
        implicit val nodeId: NodeId = NodeId(foundAction.name.value)
        val customActionParams      = foundAction.parameters
        val requestParamsMap        = getRequestParamsMap(request, customActionParams)

        val validated = validateParams(requestParamsMap, customActionParams)
        getValidationResult(validated)
      }
    }
  }

  private def getValidationResult(
      validatedParams: Either[ValidationError, Map[String, List[PartSubGraphCompilationError]]]
  ): Either[NuDesignerError, CustomActionValidationResult] = {
    val hasErrors = validatedParams.map { m => m.exists { case (_, errorList) => errorList.nonEmpty } }

    hasErrors match {
      case Right(true) =>
        Right(CustomActionValidationResult.Invalid(validatedParams.getOrElse(throw IllegalStateException)))
      case Right(false) => Right(CustomActionValidationResult.Valid)
      case Left(_)      => _
    }
  }

  private def getRequestParamsMap(request: CustomActionRequest, customActionParams: List[CustomActionParameter]) = {
    (customActionParams.nonEmpty, request.params) match {
      case (true, Some(paramsMap)) =>
        if (paramsMap.keys.size != customActionParams.size) {
          Left(
            ValidationError(
              s"Validation requires different count of custom action parameters than provided in request for: ${request.actionName}"
            )
          )
        }
        Right(paramsMap)
      case (true, None) =>
        Left(ValidationError(s"Missing required params for action: ${request.actionName}"))
      case (false, Some(_)) =>
        Left(ValidationError(s"Params found for no params action: ${request.actionName}"))
      case _ => Right(Map.empty[String, String])
    }
  }

  private def validateParams(
      requestParamsMap: Either[ValidationError, Map[String, String]],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): Either[ValidationError, Map[String, List[PartSubGraphCompilationError]]] = {
    val checkedParamsMap = checkForMissingKeys(requestParamsMap, customActionParams)

    checkedParamsMap.map { m =>
      m.map { case (k, v) =>
        (
          k,
          customActionParams.find(_.name == k) match {
            case Some(param) =>
              param.validators
                .getOrElse(Nil)
                .map {
                  _.isValid(paramName = k, expression = Expression.spel("None"), value = Some(v), label = None)
                }
                .collect { case Invalid(i) => i }
            case None =>
              throw IllegalStateException
          }
        )
      }
    }
  }

  private def checkForMissingKeys(
      requestParamsMap: Either[ValidationError, Map[String, String]],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): Either[ValidationError, Map[String, String]] = {
    requestParamsMap.flatMap { map =>
      val nameList    = customActionParams.map(_.name)
      val missingKeys = nameList.filterNot(map.contains)
      missingKeys match {
        case Nil => Right(map)
        case _   => Left(ValidationError(s"Missing params: ${missingKeys.mkString(", ")} for action: ${nodeId.id}"))
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

}

case class ValidationError(message: String) extends BadRequestError(message)

case class CustomActionNonExisting(actionName: ScenarioActionName) extends NotFoundError(s"$actionName is not existing")
