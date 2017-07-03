package pl.touk.esp.ui.api.helpers

import argonaut.CodecJson
import pl.touk.esp.ui.codec.UiCodecs
import pl.touk.esp.ui.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

object TestCodecs extends UiCodecs {
  import argonaut.ArgonautShapeless._

  implicit val nodeValidationErrorCodec = CodecJson.derive[NodeValidationError]
  implicit val validationErrorsCodec = CodecJson.derive[ValidationErrors]
  implicit val validationWarningsCodec = CodecJson.derive[ValidationWarnings]
  implicit val validationResultCodec = CodecJson.derive[ValidationResult]
}
