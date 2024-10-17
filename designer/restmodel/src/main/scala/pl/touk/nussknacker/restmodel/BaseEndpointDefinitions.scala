package pl.touk.nussknacker.restmodel

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.ToSecure
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api.{AuthenticationError, AuthorizationError, SecurityError}
import pl.touk.nussknacker.ui.security.api.SecurityError._
import sttp.model.StatusCode.{Forbidden, NotImplemented, Unauthorized}
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
              plainBody[AuthenticationError]
                .examples(
                  List(
                    Example.of(name = Some("CannotAuthenticateUser"), value = CannotAuthenticateUser),
                    Example.of(name = Some("ImpersonatedUserNotExistsError"), value = ImpersonatedUserNotExistsError)
                  )
                )
            ),
            oneOfVariantFromMatchType(
              NotImplemented,
              plainBody[ImpersonationNotSupportedError.type]
                .description("Impersonation is not supported for defined authentication mechanism")
                .example(
                  Example.of(
                    summary = Some(
                      "Cannot authenticate impersonated user as impersonation is not supported by the authentication mechanism"
                    ),
                    value = ImpersonationNotSupportedError
                  )
                )
            ),
            oneOfVariantFromMatchType(
              Forbidden,
              oneOfBody[AuthorizationError](
                plainBody[AuthorizationError]
                  .examples(
                    List(
                      Example.of(name = Some("InsufficientPermission"), value = InsufficientPermission),
                      Example
                        .of(name = Some("ImpersonationMissingPermission"), value = ImpersonationMissingPermissionError)
                    )
                  )
              )
            ),
          )
        )
    }

  }

  private object Codecs {

    implicit val authorizationErrorCodec: Codec[String, AuthorizationError, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, AuthorizationError](deserializationNotSupportedException)(s => s.errorMessage)
      )
    }

    implicit val authenticationErrorCodec: Codec[String, AuthenticationError, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, AuthenticationError](deserializationNotSupportedException)(s => s.errorMessage)
      )
    }

    implicit val impersonationNotSupportedErrorCodec
        : Codec[String, ImpersonationNotSupportedError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, ImpersonationNotSupportedError.type](_ => ImpersonationNotSupportedError)(_ =>
          ImpersonationNotSupportedError.errorMessage
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
