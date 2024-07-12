package pl.touk.nussknacker.restmodel

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.ToSecure
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api.SecurityError
import pl.touk.nussknacker.ui.security.api.SecurityError.{
  CannotAuthenticateUser,
  ImpersonatedUserDataNotFoundError,
  ImpersonationMissingPermissionError,
  ImpersonationNotSupportedError,
  InsufficientPermission
}
import sttp.model.StatusCode.{Forbidden, NotFound, NotImplemented, Unauthorized}
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
              plainBody[CannotAuthenticateUser.type]
                .example(
                  Example.of(
                    summary = Some("Authentication failed"),
                    value = CannotAuthenticateUser
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
              plainBody[InsufficientPermission.type]
                .example(
                  Example.of(
                    summary = Some("Authorization failed"),
                    value = InsufficientPermission
                  )
                )
            )
          )
        )
    }

  }

  private object Codecs {

    implicit val authenticationErrorCodec: Codec[String, CannotAuthenticateUser.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, CannotAuthenticateUser.type](_ => CannotAuthenticateUser)(_ =>
          "The supplied authentication is invalid"
        )
      )
    }

    implicit val authorizationErrorCodec: Codec[String, InsufficientPermission.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, InsufficientPermission.type](_ => InsufficientPermission)(_ =>
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
