package pl.touk.nussknacker.ui.security.oauth2

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.OAuth2CompoundException

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

trait JwtStandardClaims {
  val issuer: Option[String]
  val subject: Option[String]
  val audition: Option[List[String]]
  val expirationTime: Option[Deadline]
  val notBefore: Option[Deadline]
  val issuedAt: Option[Deadline]
  val jwtId: Option[String]
}

class JwtOAuth2Service[
  UserInfoData: Decoder,
  AuthorizationData <: OAuth2AuthorizationData : Decoder,
  JwtClaims <: JwtStandardClaims : Decoder
](clientApi: OAuth2ClientApi[UserInfoData, AuthorizationData],
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext)
  extends BaseOAuth2Service[UserInfoData, AuthorizationData](clientApi)
    with LazyLogging {

  protected val introspectAccessToken: Boolean = configuration.jwt.exists(_.accessTokenIsJwt)

  protected val jwtValidator: Option[JwtValidator] = configuration.jwt.map(_.authServerPublicKey).map(new JwtValidator(_))

  protected def introspectToken[Claims : Decoder](token: String): Option[Claims] =
    jwtValidator.map(_.introspect[Claims](token) match {
      case Valid(claims) => claims
      case Invalid(jwtErrors) => throw OAuth2CompoundException(jwtErrors)
    })

  override def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])] = {
    val expirationFromToken =
      Some(accessToken).filter(_ => introspectAccessToken).flatMap(introspectToken[JwtClaims])
        .flatMap(_.expirationTime)
    super.checkAuthorizationAndObtainUserinfo(accessToken)
      .map { case (userInfo, expiration) => (userInfo, expirationFromToken.orElse(expiration)) }
  }
}

@ConfiguredJsonCodec case class DefaultJwtAccessToken
(
  @JsonKey("iss") issuer: Option[String],
  @JsonKey("sub") subject: Option[String],
  @JsonKey("aud") audition: Option[List[String]],
  @JsonKey("exp") expirationTime: Option[Deadline],
  @JsonKey("nbf") notBefore: Option[Deadline],
  @JsonKey("iat") issuedAt: Option[Deadline],
  @JsonKey("jti") jwtId: Option[String]
) extends JwtStandardClaims

object DefaultJwtAccessToken extends EpochSecondsCodecs {
  implicit val config: Configuration = Configuration.default
}