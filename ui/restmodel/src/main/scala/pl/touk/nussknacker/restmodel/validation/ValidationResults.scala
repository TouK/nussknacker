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

object ValidationResults {

  private implicit val typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)

  //TODO: consider extracting additional DTO class
  //TODO: we have ConfiguredJsonCodec to handle empty nodeResults for legacy reasons, remove it after successful NK migration
  @ConfiguredJsonCodec case class ValidationResult(errors: ValidationErrors, warnings: ValidationWarnings,
                                                   nodeResults: Map[String, NodeTypingData] = Map.empty) {
    val isOk: Boolean = errors == ValidationErrors.success && warnings == ValidationWarnings.success
    val saveAllowed: Boolean = allErrors.forall(_.errorType == NodeValidationErrorType.SaveAllowed)

    def add(other: ValidationResult): ValidationResult = ValidationResult(
      ValidationErrors(
        errors.invalidNodes.combine(other.errors.invalidNodes),
        errors.processPropertiesErrors ++ other.errors.processPropertiesErrors,
        errors.globalErrors ++ other.errors.globalErrors),
      ValidationWarnings(
        warnings.invalidNodes.combine(other.warnings.invalidNodes)
      ),
      nodeResults ++ other.nodeResults
    )

    def withNodeResults(nodeResults: Map[String, NodeTypingData]): ValidationResult
    = copy(nodeResults = nodeResults)

    def renderNotAllowedErrors: List[NodeValidationError] = {
      allErrors.filter(_.errorType == NodeValidationErrorType.RenderNotAllowed)
    }

    def saveNotAllowedErrors: List[NodeValidationError] = {
      allErrors.filter(_.errorType == NodeValidationErrorType.SaveNotAllowed)
    }

    def withClearedTypingInfo: ValidationResult = copy(nodeResults = nodeResults.mapValues(_.copy(typingInfo = Map.empty)))

    def typingInfo: Map[String, Map[String, ExpressionTypingInfo]] = nodeResults.mapValues(_.typingInfo)

    private def allErrors: List[NodeValidationError] = {
      (errors.invalidNodes.values.flatten ++ errors.processPropertiesErrors ++ errors.globalErrors).toList
    }

  }

  object NodeTypingData {
    implicit val typingInfoEncoder: Encoder[ExpressionTypingInfo] = Encoder.instance { expressionTypingInfo =>
      TypeEncoders.typingResultEncoder.apply(expressionTypingInfo.typingResult)
    }
    implicit val typingInfoDecoder: Decoder[ExpressionTypingInfo] = Decoder.failedWithMessage("typingInfo shouldn't be decoded")
  }

  @JsonCodec case class NodeTypingData(variableTypes: Map[String, TypingResult],
                                       parameters: Option[List[UIParameter]],
                                       // currently we not showing typing info in gui but maybe in near future will
                                       // be used for enhanced typing in FE
                                       typingInfo: Map[String, ExpressionTypingInfo])

  @JsonCodec case class ValidationErrors(invalidNodes: Map[String, List[NodeValidationError]],
                                         processPropertiesErrors: List[NodeValidationError],
                                         globalErrors: List[NodeValidationError]) {
    def isEmpty: Boolean = invalidNodes.isEmpty && processPropertiesErrors.isEmpty && globalErrors.isEmpty
  }

  @JsonCodec case class ValidationWarnings(invalidNodes: Map[String, List[NodeValidationError]])

  @JsonCodec case class NodeValidationError(typ: String,
                                            message: String,
                                            description: String,
                                            fieldName: Option[String],
                                            errorType: NodeValidationErrorType.Value)

  object ValidationErrors {
    val success = ValidationErrors(Map.empty, List(), List())
  }

  object ValidationWarnings {
    val success = ValidationWarnings(Map.empty)
  }

  object ValidationResult {
    val success = ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map.empty)

    def errors(invalidNodes: Map[String, List[NodeValidationError]],
               processPropertiesErrors: List[NodeValidationError],
               globalErrors: List[NodeValidationError]): ValidationResult = {
      ValidationResult(
        ValidationErrors(invalidNodes = invalidNodes, processPropertiesErrors = processPropertiesErrors,
          globalErrors = globalErrors
        ),
        ValidationWarnings.success,
        Map.empty
      )
    }

    def warnings(invalidNodes: Map[String, List[NodeValidationError]]): ValidationResult = {
      ValidationResult(
        ValidationErrors.success,
        ValidationWarnings(invalidNodes = invalidNodes), Map.empty
      )
    }

  }

  object NodeValidationErrorType extends Enumeration {

    type NodeValidationErrorType = Value
    implicit val encoder: Encoder[NodeValidationErrorType.Value] = Encoder.enumEncoder(NodeValidationErrorType)
    implicit val decoder: Decoder[NodeValidationErrorType.Value] = Decoder.enumDecoder(NodeValidationErrorType)
    val RenderNotAllowed, SaveNotAllowed, SaveAllowed = Value
  }

}
