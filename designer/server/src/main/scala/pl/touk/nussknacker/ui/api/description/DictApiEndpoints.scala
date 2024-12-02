package pl.touk.nussknacker.ui.api.description

import io.circe.Json
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.dict.DictEntry
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints.DictError
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints.DictError.{
  MalformedTypingResult,
  NoDict,
  NoProcessingType
}
import pl.touk.nussknacker.ui.api.description.DictApiEndpoints.Dtos._
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.Schema

import scala.language.implicitConversions

class DictApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val dictionaryEntryQueryEndpoint: SecuredEndpoint[(String, String, String), DictError, List[DictEntry], Any] =
    baseNuApiEndpoint
      .summary("Get list of dictionary entries matching the label pattern")
      .tag("Dictionary")
      .get
      .in(
        "processDefinitionData" / path[String]("processingType") / "dicts" / path[String]("dictId") / "entry" / query[
          String
        ]("label")
      )
      .out(
        statusCode(Ok).and(
          jsonBody[List[DictEntry]]
        )
      )
      .errorOut(
        oneOf[DictError](
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoProcessingType]
          ),
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoDict]
          )
        )
      )
      .withSecurity(auth)

  lazy val dictionaryListEndpoint: SecuredEndpoint[(String, DictListRequestDto), DictError, List[DictDto], Any] =
    baseNuApiEndpoint
      .summary("Get list of available dictionaries with value type compatible with expected type")
      .tag("Dictionary")
      .post
      .in("processDefinitionData" / path[String]("processingType") / "dicts")
      .in(jsonBody[DictListRequestDto])
      .out(
        statusCode(Ok).and(
          jsonBody[List[DictDto]]
        )
      )
      .errorOut(
        oneOf[DictError](
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoProcessingType]
          ),
          oneOfVariantFromMatchType(
            BadRequest,
            plainBody[MalformedTypingResult]
          )
        )
      )
      .withSecurity(auth)

}

object DictApiEndpoints {

  object Dtos {

    @JsonCodec
    case class DictListRequestDto(expectedType: Json)

    @JsonCodec
    case class DictDto(
        id: String,
        label: String // TODO: introduce separate labels for dictionaries, currently this is just equal to id
    )

    implicit lazy val dictListRequestDtoSchema: Schema[DictListRequestDto] = Schema.derived[DictListRequestDto]
    implicit lazy val dictEntrySchema: Schema[DictEntry]                   = Schema.derived
    implicit lazy val dictDtoSchema: Schema[DictDto]                       = Schema.derived

  }

  sealed trait DictError

  object DictError {
    final case class NoDict(dictId: String) extends DictError

    final case class NoProcessingType(processingType: ProcessingType) extends DictError

    final case class MalformedTypingResult(msg: String) extends DictError

    implicit val noDictCodec: Codec[String, NoDict, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoDict](e =>
        s"Dictionary with id: ${e.dictId} not found"
      )
    }

    implicit val noProcessingTypeCodec: Codec[String, NoProcessingType, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoProcessingType](e =>
        s"Processing type: ${e.processingType} not found"
      )
    }

    implicit val malformedTypingResultCodec: Codec[String, MalformedTypingResult, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[MalformedTypingResult](e =>
        s"The request content was malformed:\n${e.msg}"
      )
    }

  }

}
