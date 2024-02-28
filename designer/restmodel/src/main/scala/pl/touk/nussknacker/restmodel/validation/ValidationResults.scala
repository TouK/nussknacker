package pl.touk.nussknacker.restmodel.validation

import cats.implicits._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.{TypeEncoders, typing}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object ValidationResults {

  private implicit val typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)

  // TODO: consider extracting additional DTO class
  @JsonCodec final case class ValidationResult(
      errors: ValidationErrors,
      warnings: ValidationWarnings,
      nodeResults: Map[String, NodeTypingData]
  ) {
    val hasErrors: Boolean   = errors != ValidationErrors.success
    val hasWarnings: Boolean = warnings != ValidationWarnings.success
    val saveAllowed: Boolean = allErrors.forall(_.errorType == NodeValidationErrorType.SaveAllowed)

    def add(other: ValidationResult): ValidationResult = ValidationResult(
      ValidationErrors(
        errors.invalidNodes.combine(other.errors.invalidNodes),
        errors.processPropertiesErrors ++ other.errors.processPropertiesErrors,
        errors.globalErrors ++ other.errors.globalErrors
      ),
      ValidationWarnings(
        warnings.invalidNodes.combine(other.warnings.invalidNodes)
      ),
      nodeResults ++ other.nodeResults
    )

    def withNodeResults(nodeResults: Map[String, NodeTypingData]): ValidationResult =
      copy(nodeResults = nodeResults)

    def renderNotAllowedErrors: List[NodeValidationError] =
      allErrors.filter(_.errorType == NodeValidationErrorType.RenderNotAllowed)

    def saveNotAllowedErrors: List[NodeValidationError] =
      allErrors.filter(_.errorType == NodeValidationErrorType.SaveNotAllowed)

    def typingInfo: Map[String, Map[String, ExpressionTypingInfo]] =
      nodeResults.mapValuesNow(_.typingInfo)

    private def allErrors: List[NodeValidationError] =
      (errors.invalidNodes.values.flatten ++ errors.processPropertiesErrors ++ errors.globalErrors.map(_.error)).toList

  }

  object NodeTypingData {
    implicit val typingInfoEncoder: Encoder[ExpressionTypingInfo] =
      TypeEncoders.typingResultEncoder.contramap(_.typingResult)
    // FIXME: BaseFlowTest fails otherwise??
    implicit val typingInfoDecoder: Decoder[Map[String, ExpressionTypingInfo]] =
      Decoder.const(Map.empty) // Decoder.failedWithMessage("typingInfo shouldn't be decoded")
  }

  @JsonCodec final case class NodeTypingData(
      variableTypes: Map[String, TypingResult],
      parameters: Option[List[UIParameter]],
      // currently we not showing typing info in gui but maybe in near future will
      // be used for enhanced typing in FE
      typingInfo: Map[String, ExpressionTypingInfo]
  )

  @JsonCodec final case class ValidationErrors(
      invalidNodes: Map[String, List[NodeValidationError]],
      processPropertiesErrors: List[NodeValidationError],
      globalErrors: List[UIGlobalError]
  ) {
    def isEmpty: Boolean = invalidNodes.isEmpty && processPropertiesErrors.isEmpty && globalErrors.isEmpty
  }

  @JsonCodec final case class ValidationWarnings(invalidNodes: Map[String, List[NodeValidationError]])

  // compatibility with Nu 1.14
  @JsonCodec final case class UIGlobalError(
      error: NodeValidationError,
      nodeIds: List[String],
      typ: String,
      message: String,
      description: String,
      fieldName: Option[String],
      errorType: NodeValidationErrorType.Value,
  )

  @JsonCodec final case class NodeValidationError(
      typ: String,
      message: String,
      description: String,
      fieldName: Option[String],
      errorType: NodeValidationErrorType.Value,
  )

  object ValidationErrors {
    val success: ValidationErrors = ValidationErrors(Map.empty, List(), List())
  }

  object ValidationWarnings {
    val success: ValidationWarnings = ValidationWarnings(Map.empty)
  }

  object ValidationResult {

    val success: ValidationResult = ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)

    def globalErrors(globalErrors: List[NodeValidationError]): ValidationResult =
      ValidationResult.errors(Map(), List(), globalErrors)

    def errors(
        invalidNodes: Map[String, List[NodeValidationError]],
        processPropertiesErrors: List[NodeValidationError],
        globalErrors: List[NodeValidationError]
    ): ValidationResult = {
      ValidationResult(
        ValidationErrors(
          invalidNodes = invalidNodes,
          processPropertiesErrors = processPropertiesErrors,
          globalErrors = globalErrors.map(e =>
            UIGlobalError(e, List.empty, e.typ, e.message, e.description, e.fieldName, e.errorType)
          )
        ),
        ValidationWarnings.success,
        Map.empty
      )
    }

    def warnings(invalidNodes: Map[String, List[NodeValidationError]]): ValidationResult = {
      ValidationResult(
        ValidationErrors.success,
        ValidationWarnings(invalidNodes = invalidNodes),
        Map.empty
      )
    }

  }

  object NodeValidationErrorType extends Enumeration {

    type NodeValidationErrorType = Value
    implicit val encoder: Encoder[NodeValidationErrorType.Value] = Encoder.encodeEnumeration(NodeValidationErrorType)
    implicit val decoder: Decoder[NodeValidationErrorType.Value] = Decoder.decodeEnumeration(NodeValidationErrorType)
    val RenderNotAllowed, SaveNotAllowed, SaveAllowed            = Value
  }

}
