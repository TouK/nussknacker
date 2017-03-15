package pl.touk.esp.ui.api.helpers

import java.time.LocalDateTime

import argonaut.{CodecJson, DecodeJson, EncodeJson}
import pl.touk.esp.ui.codec.{ProcessTypeCodec, ProcessingTypeCodec, UiCodecs}
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

trait TestCodecs {
  import argonaut.Argonaut._
  import argonaut.ArgonautShapeless._

  implicit val decoder = UiCodecs.displayableProcessCodec
  implicit val processTypeCodec = ProcessTypeCodec.codec
  implicit val processingTypeCodec = ProcessingTypeCodec.codec
  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))
  implicit val processListEncode = DecodeJson.of[ProcessDetails]

  implicit val nodeValidationErrorCodec = CodecJson.derive[NodeValidationError]
  implicit val validationErrorsCodec = CodecJson.derive[ValidationErrors]
  implicit val validationWarningsCodec = CodecJson.derive[ValidationWarnings]
  implicit val validationResultCodec = CodecJson.derive[ValidationResult]
}
