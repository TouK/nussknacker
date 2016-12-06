package pl.touk.esp.ui.codec

import java.time.LocalDateTime

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.graph.node
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.{DisplayableUser, GrafanaSettings, ProcessObjects}
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.esp.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessHistoryEntry}

object UiCodecs {

  import ArgonautShapeless._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //rzutujemy bo argonaut nie lubi kowariancji...
  implicit def nodeAdditionalFieldsOptCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = {
    CodecJson.derived[Option[NodeAdditionalFields]]
      .asInstanceOf[CodecJson[Option[node.UserDefinedAdditionalNodeFields]]]
  }
  implicit def processAdditionalFieldsOptCodec: CodecJson[Option[UserDefinedProcessAdditionalFields]] = {
    CodecJson.derived[Option[ProcessAdditionalFields]]
      .asInstanceOf[CodecJson[Option[UserDefinedProcessAdditionalFields]]]
  }

  implicit def propertiesCodec: CodecJson[ProcessProperties] = CodecJson.derive[ProcessProperties]

  implicit def localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit def localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))

  implicit def validationResultEncode = EncodeJson.of[ValidationResult]

  implicit def codec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  implicit def commentCodec = CodecJson.derived[Comment]
  implicit def processActivityCodec = CodecJson.derive[ProcessActivity]

  implicit def processObjectsEncodeEncode = EncodeJson.of[ProcessObjects]

  implicit def processTypeCodec = ProcessTypeCodec.codec

  implicit def processHistory = EncodeJson.of[ProcessHistoryEntry]

  implicit def processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit def grafanaEncode = EncodeJson.of[GrafanaSettings]

  implicit def userEncodeEncode = EncodeJson.of[DisplayableUser]

  implicit def printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty
}