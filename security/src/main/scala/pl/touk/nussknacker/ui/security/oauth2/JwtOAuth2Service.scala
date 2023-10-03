package pl.touk.nussknacker.ui.security.oauth2

import cats.data.NonEmptyList.one
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2AccessTokenRejection, OAuth2CompoundException}
import pl.touk.nussknacker.ui.security.oauth2.jwt.JwtValidator

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait JwtStandardClaims {
  val issuer: Option[String]
  val subject: Option[String]
  val audience: Option[Either[List[String], String]]
  final val audienceAsList: List[String] = audience.map(_.fold(identity, List(_))).toList.flatten
  val expirationTime: Option[Instant]
  val notBefore: Option[Instant]
  val issuedAt: Option[Instant]
  val jwtId: Option[String]
}

class JwtOAuth2Service[
    UserInfoData,
    AuthorizationData <: OAuth2AuthorizationData,
    AccessTokenClaims <: JwtStandardClaims: Decoder
](clientApi: OAuth2ClientApi[UserInfoData, AuthorizationData], configuration: OAuth2Configuration)(
    implicit ec: ExecutionContext
) extends BaseOAuth2Service[UserInfoData, AuthorizationData](clientApi)
    with LazyLogging {

  protected val accessTokenIsJwt: Boolean                   = configuration.jwt.exists(_.accessTokenIsJwt)
  protected val requiredAccessTokenAudience: Option[String] = configuration.jwt.flatMap(_.audience)

  protected lazy val jwtValidator: JwtValidator = new JwtValidator(_ =>
    configuration.jwt
      .flatMap(_.authServerPublicKey)
      .getOrElse(throw new NoSuchElementException("JWT configuration not found"))
  )

  protected def introspectJwtToken[Claims: Decoder](token: String): Future[Claims] =
    jwtValidator.introspect[Claims](token) match {
      case Valid(claims)     => Future.successful(claims)
      case Invalid(jwtError) => Future.failed(OAuth2CompoundException(one(jwtError)))
    }

  override def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData] = {
    if (accessTokenIsJwt) {
      introspectJwtToken[AccessTokenClaims](accessToken).map { claims =>
        if (verifyAccessTokenAudience(claims)) {
          toIntrospectedData(claims)
        } else {
          throw OAuth2CompoundException(one(OAuth2AccessTokenRejection("Invalid audience claim")))
        }
      }
    } else {
      super.introspectAccessToken(accessToken)
    }
  }

  protected def verifyAccessTokenAudience(claims: JwtStandardClaims): Boolean = {
    requiredAccessTokenAudience.isEmpty || claims.audienceAsList.exists(requiredAccessTokenAudience.contains)
  }

  protected def toIntrospectedData(claims: AccessTokenClaims): IntrospectedAccessTokenData = {
    IntrospectedAccessTokenData(claims.subject, claims.expirationTime, Set.empty)
  }

  override protected def obtainAuthorization(
      authorizationCode: String,
      redirectUri: String
  ): Future[AuthorizationData] =
    clientApi
      .accessTokenRequest(authorizationCode, redirectUri)
      .andThen { case Success(authorization) if accessTokenIsJwt => introspectAccessToken(authorization.accessToken) }

}
