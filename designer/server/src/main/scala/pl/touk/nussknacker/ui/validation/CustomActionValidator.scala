package pl.touk.nussknacker.ui.validation

import cats.data.Validated.Invalid
import io.circe.{Decoder, Encoder, HCursor, Json}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.deployment.CustomActionCommand
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.deployment.{
  CustomActionDefinition,
  CustomActionParameter,
  CustomActionValidationResult
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.CustomActionRequest
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

class CustomActionValidator(allowedAction: CustomActionDefinition) {

  def validateCustomActionParams(
      request: CustomActionRequest
  ): Either[CustomActionValidationError, CustomActionValidationResult] = {

    implicit val nodeId: NodeId = NodeId(allowedAction.name.value)
    val customActionParams      = allowedAction.parameters
    val requestParamsMap        = getRequestParamsMap(request, customActionParams)

    val validated = validateParams(requestParamsMap, customActionParams)
    getValidationResult(validated)
  }

  private def getRequestParamsMap(request: CustomActionRequest, customActionParams: List[CustomActionParameter]) = {
    (customActionParams.nonEmpty, request.params) match {
      case (true, Some(paramsMap)) =>
        if (paramsMap.keys.size != customActionParams.size) {
          Left(
            MismatchedParamsError(
              s"Validation requires different count of custom action parameters than provided in request for: ${request.actionName}"
            )
          )
        } else { Right(paramsMap) }
      case (true, None) =>
        Left(MismatchedParamsError(s"Missing required params for action: ${request.actionName}"))
      case (false, Some(_)) =>
        Left(MismatchedParamsError(s"Params found for no params action: ${request.actionName}"))
      case _ => Right(Map.empty[String, String])
    }
  }

  private def validateParams(
      requestParamsMap: Either[CustomActionValidationError, Map[String, String]],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): Either[CustomActionValidationError, Map[String, List[PartSubGraphCompilationError]]] = {
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
                  _.isValid(
                    paramName = ParameterName(k),
                    expression = Expression.spel("None"),
                    value = Some(v),
                    label = None
                  )
                }
                .collect { case Invalid(i) => i }
            case None =>
              throw new IllegalStateException
          }
        )
      }
    }
  }

  private def checkForMissingKeys(
      requestParamsMap: Either[CustomActionValidationError, Map[String, String]],
      customActionParams: List[CustomActionParameter]
  )(implicit nodeId: NodeId): Either[CustomActionValidationError, Map[String, String]] = {
    requestParamsMap.flatMap { map =>
      val nameList    = customActionParams.map(_.name)
      val missingKeys = nameList.filterNot(map.contains)
      missingKeys match {
        case Nil => Right(map)
        case _ => Left(MismatchedParamsError(s"Missing params: ${missingKeys.mkString(", ")} for action: ${nodeId.id}"))
      }
    }
  }

  private def getValidationResult(
      validatedParams: Either[CustomActionValidationError, Map[String, List[PartSubGraphCompilationError]]]
  ): Either[CustomActionValidationError, CustomActionValidationResult] = {
    val hasErrors = validatedParams.map { m => m.exists { case (_, errorList) => errorList.nonEmpty } }

    hasErrors match {
      case Right(true) =>
        Right(CustomActionValidationResult.Invalid(validatedParams.getOrElse(throw new IllegalStateException)))
      case Right(false) => Right(CustomActionValidationResult.Valid)
      case Left(va)     => Left(va)
    }
  }

  def validateCustomActionParams(
      command: CustomActionCommand
  ): Either[CustomActionValidationError, CustomActionValidationResult] = {
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

object CustomActionValidationError {
  def apply(message: String): CustomActionValidationError = new CustomActionValidationError(message)

  implicit val encodeCustomActionValidationError: Encoder[CustomActionValidationError] =
    (error: CustomActionValidationError) => Json.obj("message" -> Json.fromString(error.getMessage))

  // Decoder
  implicit val decodeCustomActionValidationError: Decoder[CustomActionValidationError] =
    (c: HCursor) =>
      for {
        message <- c.downField("message").as[String]
      } yield new CustomActionValidationError(message)

}

//GSK: Too much happens here. This error was treated as an indication group of errors and an error from this group.
// Aaand BadRequestError is from old akka ManagementResources approach.
sealed class CustomActionValidationError(message: String) extends BadRequestError(message)

//GSK: this is exception in the scope of old akka ManagementResources
case class CustomActionNonExistingError(message: String) extends NotFoundError(message)

case class MismatchedParamsError(message: String) extends CustomActionValidationError(message)
