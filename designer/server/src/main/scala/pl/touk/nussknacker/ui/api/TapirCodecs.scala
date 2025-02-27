package pl.touk.nussknacker.ui.api

import cats.implicits.toTraverseOps
import io.circe.{Codec => CirceCodec, Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.test.TestingCapabilities
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UISourceParameters}
import pl.touk.nussknacker.ui.process.test.RawScenarioTestData
import pl.touk.nussknacker.ui.server.HeadersSupport.{ContentDisposition, FileName}
import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema, Validator}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.CodecFormat.TextPlain

import java.net.URL

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

  object ScenarioGraphCodec {
    // FIXME: Type me properly
    implicit val scenarioGraphSchema: Schema[ScenarioGraph] = Schema.anyObject
  }

  object ProcessNameCodec {
    implicit val processNameSchema: Schema[ProcessName] = Schema.string
  }

  object ScenarioTestingCodecs {

    implicit val testingCapabilitiesSchema: Schema[TestingCapabilities] = Schema.derived
    implicit val uiSourceParametersSchema: Schema[UISourceParameters]   = Schema.anyObject
    implicit val typingResultDecoder: Decoder[TypingResult]             = Decoder.decodeJson.map(_ => typing.Unknown)

  }

  object URLCodec {

    implicit val circeCodec: CirceCodec[URL] = {
      CirceCodec.from(
        Decoder.decodeURI.map(_.toURL),
        Encoder.encodeJson.contramap[URL](url => Json.fromString(url.toString))
      )
    }

    implicit val urlSchema: Schema[URL]        = Schema.string[URL]
    implicit val urlSchemas: Schema[List[URL]] = urlSchema.asIterable[List]
  }

  object ClassCodec {
    implicit val classSchema: Schema[Class[_]] = Schema.string[Class[_]]
  }

  def enumSchema[T](
      items: List[T],
      encoder: T => String,
  ): Schema[T] =
    Schema.string.validate(
      Validator.enumeration(
        items,
        (i: T) => Some(encoder(i)),
      ),
    )

}
