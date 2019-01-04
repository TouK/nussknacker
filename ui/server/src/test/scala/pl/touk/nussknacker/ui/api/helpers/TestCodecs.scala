package pl.touk.nussknacker.ui.api.helpers

import argonaut.CodecJson
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

object TestCodecs extends UiCodecs {
  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.engine.api.typed.TypeEncoders._

  implicit val nodeValidationErrorCodec = CodecJson.derive[NodeValidationError]
  implicit val validationErrorsCodec = CodecJson.derive[ValidationErrors]
  implicit val validationWarningsCodec = CodecJson.derive[ValidationWarnings]
  implicit val validationResultCodec = CodecJson.derive[ValidationResult]
}
