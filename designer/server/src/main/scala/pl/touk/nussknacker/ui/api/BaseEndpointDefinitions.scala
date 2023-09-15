package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode.{Forbidden, Unauthorized}
import sttp.tapir.EndpointInput.Auth
import sttp.tapir._

abstract class BaseEndpointDefinitions(auth: Auth[AuthCredentials, _]) {

  val baseNuApiEndpoint =
    // TODO: when all services are moved to Tapir (including authn & authz), we can uncomment this path here
    endpoint.in("api2")

  val baseNuApiPublicEndpoint =
    baseNuApiEndpoint

//  type SecuredEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, -R] =
//    Endpoint[AuthCredentials, INPUT, Either[ERROR_OUTPUT, SecurityError], OUTPUT, R]
//
//  private def baseNuApiUserSecuredEndpoint =
//    baseNuApiEndpoint
//      .securityIn(auth)
//      .errorOutEither(
//        oneOf(
//          oneOfVariantFromMatchType(Unauthorized, emptyOutputAs(SecurityError.AuthenticationError)),
//          oneOfVariantFromMatchType(Forbidden, emptyOutputAs(SecurityError.AuthorizationError)),
//        )
//      )

  def allEndpoints: List[AnyEndpoint]

}
object BaseEndpointDefinitions {
  type EndpointError[ERROR] = Either[SecurityError, ERROR]

  implicit class ToSecure[INPUT, ERROR_OUTPUT, OUTPUT, -R](val endpoint: Endpoint[Unit, INPUT, ERROR_OUTPUT, OUTPUT, R]) extends AnyVal {

    import Codecs._

    def withSecurity(auth: Auth[AuthCredentials, _]): Endpoint[AuthCredentials, INPUT, Either[ERROR_OUTPUT, SecurityError], OUTPUT, R] = {
      endpoint
        .securityIn(auth)
        .errorOutEither(
          oneOf(
            oneOfVariantFromMatchType(Unauthorized, plainBody[SecurityError.AuthenticationError.type]),
            oneOfVariantFromMatchType(Forbidden, plainBody[SecurityError.AuthorizationError.type])
          )
        )
    }
  }

  private object Codecs {
    implicit val authenticationErrorCodec: Codec[String, SecurityError.AuthenticationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, SecurityError.AuthenticationError.type](
          _ => SecurityError.AuthenticationError
        )(
          _ => "The supplied authentication is invalid"
        )
      )
    }

    implicit val authorizationErrorCodec: Codec[String, SecurityError.AuthorizationError.type, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, SecurityError.AuthorizationError.type](
          _ => SecurityError.AuthorizationError
        )(
          _ => "The supplied authentication is not authorized to access this resource"
        )
      )
    }
  }
}

sealed trait SecurityError
object SecurityError {
  case object AuthenticationError extends SecurityError
  case object AuthorizationError extends SecurityError
}
