package pl.touk.nussknacker.restmodel.validation

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing

object ValidationResults {

  private implicit val typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)

  @JsonCodec case class ValidationResult(errors: ValidationErrors, warnings: ValidationWarnings,
                                         variableTypes: Map[String, Map[String, TypingResult]],
                                         // currently we not showing typing info in gui but maybe in near future will
                                         // be used for enhanced typing in FE
                                         typingInfo: Map[String, Map[String, ExpressionTypingInfo]]) {
    val isOk: Boolean = errors == ValidationErrors.success && warnings == ValidationWarnings.success
    val saveAllowed: Boolean = allErrors.forall(_.errorType == NodeValidationErrorType.SaveAllowed)

    def add(other: ValidationResult) = ValidationResult(
      ValidationErrors(
        errors.invalidNodes.combine(other.errors.invalidNodes),
        errors.processPropertiesErrors ++ other.errors.processPropertiesErrors,
        errors.globalErrors ++ other.errors.globalErrors),
      ValidationWarnings(
        warnings.invalidNodes.combine(other.warnings.invalidNodes)
      ),
      variableTypes ++ other.variableTypes,
      typingInfo ++ other.typingInfo
    )

    def withTypes(variableTypes: Map[String, Map[String, TypingResult]]): ValidationResult
      = copy(variableTypes = variableTypes)

    def withTypingInfo(typingInfo: Map[String, Map[String, ExpressionTypingInfo]]): ValidationResult
    = copy(typingInfo = typingInfo)

    def renderNotAllowedErrors: List[NodeValidationError] = {
      allErrors.filter(_.errorType == NodeValidationErrorType.RenderNotAllowed)
    }

    def saveNotAllowedErrors: List[NodeValidationError] = {
      allErrors.filter(_.errorType == NodeValidationErrorType.SaveNotAllowed)
    }

    private def allErrors: List[NodeValidationError] = {
      (errors.invalidNodes.values.flatten ++ errors.processPropertiesErrors ++ errors.globalErrors).toList
    }

    def withClearedTypingInfo: ValidationResult = copy(typingInfo = Map.empty)

  }

  @JsonCodec case class ValidationErrors(invalidNodes: Map[String, List[NodeValidationError]],
                              processPropertiesErrors: List[NodeValidationError],
                              globalErrors: List[NodeValidationError]) {
    def isEmpty: Boolean = invalidNodes.isEmpty && processPropertiesErrors.isEmpty && globalErrors.isEmpty
  }
  object ValidationErrors {
    val success = ValidationErrors(Map.empty, List(), List())
  }

  @JsonCodec case class ValidationWarnings(invalidNodes: Map[String, List[NodeValidationError]])
  object ValidationWarnings {
    val success = ValidationWarnings(Map.empty)
  }

  object ValidationResult {
    val success = ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty, Map.empty)

    def errors(invalidNodes: Map[String, List[NodeValidationError]],
               processPropertiesErrors: List[NodeValidationError],
               globalErrors: List[NodeValidationError],
               variableTypes: Map[String, Map[String, TypingResult]] = Map.empty): ValidationResult = {
      ValidationResult(
        ValidationErrors(invalidNodes = invalidNodes, processPropertiesErrors = processPropertiesErrors,
          globalErrors = globalErrors
        ),
        ValidationWarnings.success,
        variableTypes,
        Map.empty
      )
    }

    def warnings(invalidNodes: Map[String, List[NodeValidationError]]): ValidationResult = {
      ValidationResult(
        ValidationErrors.success,
        ValidationWarnings(invalidNodes = invalidNodes), Map.empty, Map.empty
      )
    }

    implicit val typingInfoEncoder: Encoder[ExpressionTypingInfo] = Encoder.instance(_ => throw new IllegalStateException("typingInfo shouldn't be encoded"))
    implicit val typingInfoDecoder: Decoder[ExpressionTypingInfo] = Decoder.failedWithMessage("typingInfo shouldn't be decoded")

  }

  @JsonCodec case class NodeValidationError(typ: String,
                                 message: String,
                                 description: String,
                                 fieldName: Option[String],
                                 errorType: NodeValidationErrorType.Value
                                )

  object NodeValidationErrorType extends Enumeration {

    implicit val encoder: Encoder[NodeValidationErrorType.Value] = Encoder.enumEncoder(NodeValidationErrorType)
    implicit val decoder: Decoder[NodeValidationErrorType.Value] = Decoder.enumDecoder(NodeValidationErrorType)

    type NodeValidationErrorType = Value
    val RenderNotAllowed, SaveNotAllowed, SaveAllowed = Value
  }

}
