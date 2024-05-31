package pl.touk.nussknacker.restmodel

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.ToSecure
import pl.touk.nussknacker.security.AuthCredentials
import sttp.model.StatusCode.{Forbidden, Unauthorized}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._

import scala.language.implicitConversions

trait BaseEndpointDefinitions {

  val baseNuApiEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] = endpoint.in("api")

  implicit def toSecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R](
      endpoint: Endpoint[Unit, INPUT, BUSINESS_ERROR, OUTPUT, R]
  ): ToSecure[INPUT, BUSINESS_ERROR, OUTPUT, R] =
    new ToSecure(endpoint)

}

object BaseEndpointDefinitions {

  type EndpointError[ERROR] = Either[SecurityError, ERROR]
  type SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, -R] =
    Endpoint[AuthCredentials, INPUT, Either[BUSINESS_ERROR, SecurityError], OUTPUT, R]

  implicit class ToSecure[INPUT, BUSINESS_ERROR, OUTPUT, -R](
      val endpoint: PublicEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]
  ) extends AnyVal {

    import Codecs._

    def withSecurity(auth: EndpointInput[AuthCredentials]): SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R] = {
      endpoint
        .securityIn(auth)
        .errorOutEither(
          oneOf(
            oneOfVariantFromMatchType(
              Unauthorized,
              plainBody[SecurityError.AuthenticationError.type]
                .example(
                  Example.of(
                    summary = Some("Authentication failed"),
                    value = SecurityError.AuthenticationError
                  )
                )
            ),
            oneOfVariantFromMatchType(
              Forbidden,
              plainBody[SecurityError.ImpersonationError.type]
                .example(
                  Example.of(
                    summary = Some("Authorization failed"),
                    value = SecurityError.ImpersonationError
                  )
                )
            ),
            oneOfVariantFromMatchType(
              Forbidden,
              plainBody[SecurityError.AuthorizationError.type]
                .example(
                  Example.of(
                    summary = Some("Authorization failed"),
                    value = SecurityError.AuthorizationError
                  )
                )
            )
          )
        )
    }

  }

  private object Codecs {

    implicit val authenticationErrorCodec
        : Codec[String, SecurityError.AuthenticationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, SecurityError.AuthenticationError.type](_ => SecurityError.AuthenticationError)(_ =>
          "The supplied authentication is invalid"
        )
      )
    }

    implicit val authorizationErrorCodec
        : Codec[String, SecurityError.AuthorizationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, SecurityError.AuthorizationError.type](_ => SecurityError.AuthorizationError)(_ =>
          "The supplied authentication is not authorized to access this resource"
        )
      )
    }

    implicit val impersonationErrorCodec
        : Codec[String, SecurityError.ImpersonationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, SecurityError.ImpersonationError.type](_ => SecurityError.ImpersonationError)(_ =>
          "The supplied authentication is not authorized to impersonate"
        )
      )
    }

  }

  def toTextPlainCodecSerializationOnly[T](
      toMessage: T => String
  ): Codec[String, T, CodecFormat.TextPlain] =
    Codec.string.map(
      Mapping.from[String, T](deserializationNotSupportedException)(toMessage)
    )

  private def deserializationNotSupportedException =
    (_: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

}

sealed trait SecurityError

object SecurityError {
  case object AuthenticationError extends SecurityError
  case object AuthorizationError  extends SecurityError
  case object ImpersonationError  extends SecurityError
}
