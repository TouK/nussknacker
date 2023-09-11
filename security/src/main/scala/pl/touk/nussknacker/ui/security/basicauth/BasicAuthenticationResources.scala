package pl.touk.nussknacker.ui.security.basicauth

import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import pl.touk.nussknacker.ui.security.api._
import sttp.tapir.{Codec, CodecFormat, Mapping, Schema}

import scala.concurrent.{ExecutionContext, Future}

class BasicAuthenticationResources(realm: String, configuration: BasicAuthenticationConfiguration)
                                  (implicit executionContext: ExecutionContext)
  extends AuthenticationResources
    with AnonymousAccess {

  val name: String = configuration.name

  val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.Browser

  val anonymousUserRole: Option[String] = configuration.anonymousUserRole

  private val authenticator = BasicHttpAuthenticator(configuration)

  def authenticateReally(): AuthenticationDirective[AuthenticatedUser] =
    SecurityDirectives.authenticateBasicAsync(
      authenticator = authenticator,
      realm = realm
    )

  // todo:
  private implicit val authCredentialsCodec: Codec[String, AuthCredentials, CodecFormat.TextPlain] =
    Codec
      .id(CodecFormat.TextPlain(), Schema.string[String])
      .map(
        Mapping.from[String, AuthCredentials](AuthCredentials.apply)(_.value)
      )

  override def authenticationMethod(): sttp.tapir.EndpointInput.Auth[AuthCredentials, _] =
    sttp.tapir.auth.basic[AuthCredentials]()

  override def authenticate(authCredentials: AuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(authCredentials)
  }
}