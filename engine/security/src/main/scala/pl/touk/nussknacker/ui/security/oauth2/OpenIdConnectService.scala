package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.BaseOAuth2Service.millisToDeadline

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

trait OpenIdConnectAccessTokenResponse extends StandardAccessTokenResponse {
  val id_token: Option[String]
}

class OpenIdConnectService[
  ProfileResponse: Decoder,
  AccessTokenResponse <: OpenIdConnectAccessTokenResponse : Decoder,
  DecodedJwtAccessToken <: JwtStandardClaims : Decoder
](clientApi: OAuth2ClientApi[ProfileResponse, AccessTokenResponse],
  createLoggedUser: (ProfileResponse) => LoggedUser,
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext)
  extends JwtOAuth2Service[ProfileResponse, AccessTokenResponse, DecodedJwtAccessToken](clientApi, createLoggedUser, configuration)
    with LazyLogging {

  protected val useIdToken: Boolean = configuration.jwt.exists(_.userinfoFromIdToken)

  override protected def getProfile(resp: AccessTokenResponse): Future[(ProfileResponse, Option[Deadline])] = {
    val profileFromIdToken: Option[ProfileResponse] =
      resp.id_token.filter(_ => useIdToken).flatMap(introspectToken[ProfileResponse])
    profileFromIdToken.map((_, resp.expires_in.map(millisToDeadline))).map(Future(_)).getOrElse(super.getProfile(resp))
  }
}
