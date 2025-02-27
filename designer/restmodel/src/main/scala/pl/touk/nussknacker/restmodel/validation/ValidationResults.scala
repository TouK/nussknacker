package pl.touk.nussknacker.restmodel.validation

import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.ErrorDetails
import pl.touk.nussknacker.engine.api.typed.{typing, TypeEncoders}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition.UIParameter

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
      // variableTypes are needed because we hold the draft of a scenario on the FE side and we don't want FE
      // to send to BE the whole scenario graph every time when someone change something in a single node.
      // Because of that we send variable types that are before each node, and FE send to BE only these types instead of the whole scenario
      variableTypes: Map[String, TypingResult],
      // It it used for node parameter adjustment on FE side (see ParametersUtils.ts -> adjustParameters) in
      // a chain of information about parameter definition. It will be used only when:
      // - API of a dynamic component was changed
      // - FE didn't send node validation request yet
      // TODO: We should remove this and instead of this, we should do node's parameters adjustment
      //       before we return the scenario graph on the BE side
      parameters: Option[List[UIParameter]],
      // typingInfo is returned to present inferred types of a parameters - it is in the separate map instead of kept with
      // parameters, because we have a hardcoded parameters for built-in components
      // We could return just a TypingResult (without intermediateResults) but it would require copying of nested
      // structures. Because of that, we remove these information on the encoding level
      typingInfo: Map[String, ExpressionTypingInfo]
  )

  @JsonCodec final case class ValidationErrors(
      invalidNodes: Map[String, List[NodeValidationError]],
      processPropertiesErrors: List[NodeValidationError],
      globalErrors: List[UIGlobalError]
  ) {
    def isEmpty: Boolean = invalidNodes.isEmpty && processPropertiesErrors.isEmpty && globalErrors.isEmpty
  }

  @JsonCodec final case class UIGlobalError(error: NodeValidationError, nodeIds: List[String])

  @JsonCodec final case class ValidationWarnings(invalidNodes: Map[String, List[NodeValidationError]])

  @JsonCodec final case class NodeValidationError(
      typ: String,
      message: String,
      description: String,
      fieldName: Option[String],
      errorType: NodeValidationErrorType.Value,
      details: Option[ErrorDetails]
  )

  object ValidationErrors {
    val success: ValidationErrors = ValidationErrors(Map.empty, List.empty, List.empty)
  }

  object ValidationWarnings {
    val success: ValidationWarnings = ValidationWarnings(Map.empty)
  }

  object ValidationResult {

    val success: ValidationResult = ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)

    def globalErrors(globalErrors: List[UIGlobalError]): ValidationResult =
      ValidationResult.errors(Map.empty, List.empty, globalErrors)

    def errors(
        invalidNodes: Map[String, List[NodeValidationError]],
        processPropertiesErrors: List[NodeValidationError],
        globalErrors: List[UIGlobalError]
    ): ValidationResult = {
      ValidationResult(
        ValidationErrors(
          invalidNodes = invalidNodes,
          processPropertiesErrors = processPropertiesErrors,
          globalErrors = globalErrors
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
