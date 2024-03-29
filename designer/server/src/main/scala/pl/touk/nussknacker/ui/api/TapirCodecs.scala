package pl.touk.nussknacker.ui.api

import cats.implicits.{toFunctorOps, toTraverseOps}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  MigrateScenarioRequest,
  MigrateScenarioRequestV1,
  MigrateScenarioRequestV2
}
import pl.touk.nussknacker.ui.server.HeadersSupport.{ContentDisposition, FileName}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}

object TapirCodecs {

  object ScenarioNameCodec {
    def encode(scenarioName: ProcessName): String = scenarioName.value

    def decode(s: String): DecodeResult[ProcessName] = {
      val scenarioName = ProcessName.apply(s)
      DecodeResult.Value(scenarioName)
    }

    implicit val scenarioNameCodec: PlainCodec[ProcessName] = Codec.string.mapDecode(decode)(encode)

    implicit val schema: Schema[ProcessName] = Schema.string

  }

  object VersionIdCodec {
    def encode(versionId: VersionId): Long = versionId.value

    def decode(value: Long): DecodeResult[VersionId] = {
      DecodeResult.Value(VersionId.apply(value))
    }

    implicit val versionIdCodec: PlainCodec[VersionId] = Codec.long.mapDecode(decode)(encode)

    implicit val schema: Schema[VersionId] = versionIdCodec.schema
  }

  object ContentDispositionCodec {

    implicit val requiredFileNameCodec: Codec[List[String], FileName, TextPlain] =
      new Codec[List[String], FileName, TextPlain] {

        override def rawDecode(headers: List[String]): DecodeResult[FileName] =
          (
            headers
              .map(h => ContentDisposition(h).fileName)
              .sequence
              .flatMap(_.headOption)
          ) match {
            case Some(value) => DecodeResult.Value(value)
            case None        => DecodeResult.Missing
          }

        override def encode(v: FileName): List[String] = ContentDisposition(Some(v)).headerValue().toList

        override def schema: Schema[FileName] = Schema.string[FileName]

        override def format: TextPlain = CodecFormat.TextPlain()
      }

  }

  object HeaderCodec {

    implicit val requiredHeaderCodec: Codec[List[String], String, TextPlain] =
      new Codec[List[String], String, TextPlain] {

        override def rawDecode(headers: List[String]): DecodeResult[String] =
          headers.headOption match {
            case Some(value) => DecodeResult.Value(value)
            case None        => DecodeResult.Missing
          }

        override def encode(v: String): List[String] = List(v)

        override def schema: Schema[String] = Schema.string

        override def format: TextPlain = CodecFormat.TextPlain()
      }

    implicit val optionalHeaderCodec: Codec[List[String], Option[String], TextPlain] =
      new Codec[List[String], Option[String], TextPlain] {

        override def rawDecode(headers: List[String]): DecodeResult[Option[String]] =
          DecodeResult.Value(headers.headOption)

        override def encode(v: Option[String]): List[String] = v.toList

        override def schema: Schema[Option[String]] = Schema.schemaForOption(Schema.schemaForString)

        override def format: TextPlain = CodecFormat.TextPlain()
      }

  }

  object ProcessingModeCodec {
    implicit val processingModeSchema: Schema[ProcessingMode] = Schema.string
  }

  object EngineSetupNameCodec {
    implicit val engineSetupNameSchema: Schema[EngineSetupName] = Schema.string
  }

  object ProcessNameCodec {
    implicit val processNameSchema: Schema[ProcessName] = Schema.string
  }

  object MigrateScenarioRequestV1Codec {
    // TODO: type me properly, see: https://github.com/TouK/nussknacker/pull/5612#discussion_r1514063218
    implicit val migrateScenarioRequestV1Schema: Schema[MigrateScenarioRequestV1] = Schema.anyObject
  }

  object MigrateScenarioRequestV2Codec {
    // TODO: type me properly, see: https://github.com/TouK/nussknacker/pull/5612#discussion_r1514063218
    implicit val migrateScenarioRequestV2Schema: Schema[MigrateScenarioRequestV2] = Schema.anyObject
  }

  object MigrateScenarioRequestCodec {
    // TODO: type me properly, see: https://github.com/TouK/nussknacker/pull/5612#discussion_r1514063218
    implicit val migrateScenarioRequestSchema: Schema[MigrateScenarioRequest] = Schema.anyObject

    implicit val migrateScenarioRequestEncoder: Encoder[MigrateScenarioRequest] = Encoder.instance {
      case v1 @ MigrateScenarioRequestV1(_, _, _, _, _, _, _) => v1.asJson
      case v2 @ MigrateScenarioRequestV2(_, _, _, _, _, _, _) => v2.asJson
    }

    implicit val migrateScenarioRequestDecoder: Decoder[MigrateScenarioRequest] = List[Decoder[MigrateScenarioRequest]](
      Decoder[MigrateScenarioRequestV1].widen,
      Decoder[MigrateScenarioRequestV2].widen,
    ).reduceLeft(_ or _)

  }

}
