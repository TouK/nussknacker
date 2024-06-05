package pl.touk.nussknacker.restmodel

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.ToSecure
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api.SecurityError
import pl.touk.nussknacker.ui.security.api.SecurityError.{
  BaseAuthenticationError,
  BaseAuthorizationError,
  ImpersonatedUserDataNotFoundError,
  ImpersonationMissingPermissionError
}
import sttp.model.StatusCode.{Forbidden, NotFound, Unauthorized}
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
              plainBody[BaseAuthenticationError.type]
                .example(
                  Example.of(
                    summary = Some("Authentication failed"),
                    value = BaseAuthenticationError
                  )
                )
            ),
            oneOfVariantFromMatchType(
              NotFound,
              plainBody[ImpersonatedUserDataNotFoundError.type]
                .description("Identity provided in the Nu-Impersonate-User-Identity header did not match any user")
                .example(
                  Example.of(
                    summary = Some("No impersonated user's data found for provided identity"),
                    value = ImpersonatedUserDataNotFoundError
                  )
                )
            ),
            oneOfVariantFromMatchType(
              Forbidden,
              plainBody[ImpersonationMissingPermissionError.type]
                .example(
                  Example.of(
                    summary = Some("Authorization failed, user does not have permission to impersonate"),
                    value = ImpersonationMissingPermissionError
                  )
                )
            ),
            oneOfVariantFromMatchType(
              Forbidden,
              plainBody[BaseAuthorizationError.type]
                .example(
                  Example.of(
                    summary = Some("Authorization failed"),
                    value = BaseAuthorizationError
                  )
                )
            )
          )
        )
    }

  }

  private object Codecs {

    implicit val authenticationErrorCodec: Codec[String, BaseAuthenticationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, BaseAuthenticationError.type](_ => BaseAuthenticationError)(_ =>
          "The supplied authentication is invalid"
        )
      )
    }

    implicit val authorizationErrorCodec: Codec[String, BaseAuthorizationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, BaseAuthorizationError.type](_ => BaseAuthorizationError)(_ =>
          "The supplied authentication is not authorized to access this resource"
        )
      )
    }

    implicit val impersonationPermissionErrorCodec
        : Codec[String, ImpersonationMissingPermissionError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, ImpersonationMissingPermissionError.type](_ => ImpersonationMissingPermissionError)(_ =>
          ImpersonationMissingPermissionError.errorMessage
        )
      )
    }

    implicit val impersonatedDataNotFoundErrorCodec
        : Codec[String, ImpersonatedUserDataNotFoundError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, ImpersonatedUserDataNotFoundError.type](_ => ImpersonatedUserDataNotFoundError)(_ =>
          ImpersonatedUserDataNotFoundError.errorMessage
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
