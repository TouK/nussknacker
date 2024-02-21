package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.Uppercase
import enumeratum._
import io.circe.syntax.EncoderOps
import io.circe.{Codec => CirceCodec, Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil.HCursorExt
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import BaseEndpointDefinitions.SecuredEndpoint
import io.circe.derivation.deriveCodec
import pl.touk.nussknacker.engine.api.process.ProcessName._
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference
import pl.touk.nussknacker.ui.{
  BadRequestError,
  FatalError,
  IllegalOperationError,
  NotFoundError,
  NuDesignerError,
  OtherError,
  UnauthorizedError
}
import sttp.model.StatusCode.{InternalServerError, NoContent, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.codec.enumeratum._
import sttp.tapir.derevo
import sttp.tapir.derevo.schema
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.circe.jsonBody
import sttp.model.StatusCode.{BadRequest, Conflict, InternalServerError, NotFound, Ok, Unauthorized}

class RemoteEnvironmentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import RemoteEnvironmentApiEndpoints.Dtos._
  import TapirCodecs.ScenarioNameCodec._
  import TapirCodecs.VersionIdCodec._

  val nuDesignerErrorOutput: EndpointOutput.OneOf[NuDesignerError, NuDesignerError] =
    oneOf[NuDesignerError](
      oneOfVariant(
        NotFound,
        plainBody[NotFoundError]
          .example(
            Example.of(???)
          )
      ),
      oneOfVariant(
        BadRequest,
        plainBody[BadRequestError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        Unauthorized,
        plainBody[UnauthorizedError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        Conflict,
        plainBody[IllegalOperationError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[OtherError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[FatalError]
          .example(Example.of(???))
      )
    )

}

object RemoteEnvironmentApiEndpoints {

  object Dtos {
    def deserializationException =
      (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

    implicit val notFoundErrorCodec: Codec[String, NotFoundError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, NotFoundError](deserializationException)(_.getMessage)
      )

    implicit val badRequestErrorCodec: Codec[String, BadRequestError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, BadRequestError](deserializationException)(_.getMessage)
      )

    implicit val unauthorizedErrorCodec: Codec[String, UnauthorizedError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, UnauthorizedError](deserializationException)(_.getMessage)
      )

    implicit val illegalOperationErrorCodec: Codec[String, IllegalOperationError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, IllegalOperationError](deserializationException)(_.getMessage)
      )

    implicit val otherErrorCodec: Codec[String, OtherError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, OtherError](deserializationException)(_.getMessage)
      )

    implicit val fatalErrorCodec: Codec[String, FatalError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, FatalError](deserializationException)(_.getMessage)
      )

  }

}
