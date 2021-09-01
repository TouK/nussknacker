package pl.touk.nussknacker.ui.security.oauth2

import cats.data.NonEmptyList.one
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2AccessTokenRejection, OAuth2CompoundException}

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait JwtStandardClaims {
  val issuer: Option[String]
  val subject: Option[String]
  val audience: Option[Either[List[String], String]]
  final val audienceAsList: List[String] = audience.map(_.fold(identity, List(_))).toList.flatten
  val expirationTime: Option[Deadline]
  val notBefore: Option[Deadline]
  val issuedAt: Option[Deadline]
  val jwtId: Option[String]
}

class JwtOAuth2Service[
  UserInfoData: Decoder,
  AuthorizationData <: OAuth2AuthorizationData : Decoder,
  AccessTokenClaims <: JwtStandardClaims : Decoder
](clientApi: OAuth2ClientApi[UserInfoData, AuthorizationData],
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext)
  extends BaseOAuth2Service[UserInfoData, AuthorizationData](clientApi)
    with LazyLogging {

  protected val accessTokenIsJwt: Boolean = configuration.jwt.exists(_.accessTokenIsJwt)
  protected val requiredAccessTokenAudience: Option[String] = configuration.jwt.flatMap(_.audience)

  protected lazy val jwtValidator: JwtValidator = new JwtValidator(_ => configuration.jwt
    .flatMap(_.authServerPublicKey).getOrElse(throw new NoSuchElementException("JWT configuration not found")))

  protected def introspectJwtToken[Claims : Decoder](token: String): Future[Claims] = jwtValidator.introspect[Claims](token) match {
    case Valid(claims) => Future.successful(claims)
    case Invalid(jwtErrors) => Future.failed(OAuth2CompoundException(jwtErrors))
  }

  override def introspectAccessToken(accessToken: String): Future[Option[Deadline]] = {
    if (accessTokenIsJwt) {
      Future(accessToken)
        .flatMap(accessToken => introspectJwtToken[AccessTokenClaims](accessToken))
        .flatMap(claims =>
          if (requiredAccessTokenAudience.isEmpty || claims.audienceAsList.exists(requiredAccessTokenAudience.contains))
            Future.successful(claims.expirationTime)
          else
            Future.failed(OAuth2CompoundException(one(OAuth2AccessTokenRejection("Invalid audience claim"))))
        )
    } else {
      super.introspectAccessToken(accessToken)
    }
  }

  override protected def obtainAuthorization(authorizationCode: String, redirectUri: String): Future[AuthorizationData] =
    clientApi.accessTokenRequest(authorizationCode, redirectUri)
      .andThen { case Success(authorization) if accessTokenIsJwt => introspectAccessToken(authorization.accessToken) }
}

@ConfiguredJsonCodec(decodeOnly = true) case class DefaultJwtAccessToken
(
  @JsonKey("iss") issuer: Option[String],
  @JsonKey("sub") subject: Option[String],
  @JsonKey("aud") audience: Option[Either[List[String], String]],
  @JsonKey("exp") expirationTime: Option[Deadline],
  @JsonKey("nbf") notBefore: Option[Deadline],
  @JsonKey("iat") issuedAt: Option[Deadline],
  @JsonKey("jti") jwtId: Option[String]
) extends JwtStandardClaims

object DefaultJwtAccessToken extends EpochSecondsCodecs with EitherCodecs {
  implicit val config: Configuration = Configuration.default
}