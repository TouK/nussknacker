package pl.touk.nussknacker.ui.security.oidc

import io.circe.{Decoder, Json}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import pl.touk.nussknacker.ui.security.oauth2._

import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}

@ConfiguredJsonCodec(decodeOnly = true) final case class OidcUserInfo(
    // Although the `sub` field is optional claim for a JWT, it becomes mandatory in OIDC context,
    // hence Some[] overrides here Option[] from JwtStandardClaims.
    @JsonKey("sub") subject: Some[String],
    name: Option[String],
    @JsonKey("given_name") givenName: Option[String],
    @JsonKey("family_name") familyName: Option[String],
    @JsonKey("middle_name") middleName: Option[String],
    nickname: Option[String],
    @JsonKey("preferred_username") preferredUsername: Option[String],
    profile: Option[String],
    picture: Option[String],
    website: Option[String],
    email: Option[String],
    @JsonKey("email_verified") emailVerified: Option[Boolean],
    gender: Option[String],
    birthdate: Option[LocalDate],
    zoneinfo: Option[String],
    locale: Option[String],
    @JsonKey("phone_number") phoneNumber: Option[String], // RFC 3963
    @JsonKey("phone_number_verified") phoneNumberVerified: Option[Boolean],
    address: Option[Map[String, String]],
    @JsonKey("updated_at") updatedAt: Option[Instant],
    @JsonKey("iss") issuer: Option[String],
    @JsonKey("aud") audience: Option[Either[List[String], String]],

    // All the following are set only when the userinfo is from an ID token
    @JsonKey("exp") expirationTime: Option[Instant],
    @JsonKey("iat") issuedAt: Option[Instant],
    @JsonKey("auth_time") authenticationTime: Option[Instant],

    // Not a standard but convenient claim that can be used by a few Authorization Server implementations.
    // The key name is taken from the rolesClaim field in the configuration.
    roles: Set[String] = Set.empty
) extends JwtStandardClaims {
  val jwtId: Option[String]      = None
  val notBefore: Option[Instant] = None
}

object OidcUserInfo extends EitherCodecs with EpochSecondsCodecs {
  implicit val config: Configuration = Configuration.default.withDefaults

  lazy val decoder: Decoder[OidcUserInfo] = deriveConfiguredDecoder[OidcUserInfo]

  def decoderWithCustomRolesClaim(rolesClaims: List[String]): Decoder[OidcUserInfo] =
    decoder.prepare {
      _.withFocus(_.mapObject { jsonObject =>
        val allRoles = rolesClaims.flatMap { roleClaim =>
          jsonObject.apply(roleClaim).flatMap(_.asArray)
        }.flatten
        jsonObject.add("roles", Json.fromValues(allRoles))
      })
    }

  def decoderWithCustomRolesClaim(rolesClaims: Option[List[String]]): Decoder[OidcUserInfo] =
    rolesClaims.map(decoderWithCustomRolesClaim).getOrElse(decoder)
}

class OidcProfileAuthentication(configuration: OAuth2Configuration) extends AuthenticationStrategy[OidcUserInfo] {

  override def authenticateUser(
      accessTokenData: IntrospectedAccessTokenData,
      getProfile: => Future[OidcUserInfo]
  )(implicit ec: ExecutionContext): Future[AuthenticatedUser] = {
    authenticateUserBasedOnAccessTokenDataAndUsersConfigurationOnly(accessTokenData, configuration).getOrElse(
      authenticateUserBasedOnProfile(accessTokenData, getProfile, configuration)
    )
  }

  private def authenticateUserBasedOnAccessTokenDataAndUsersConfigurationOnly(
      accessTokenData: IntrospectedAccessTokenData,
      configuration: OAuth2Configuration
  ) = {
    for {
      definedAccessTokenSubject <- accessTokenData.subject
      configUser                <- configuration.findUserById(definedAccessTokenSubject)
      username                  <- configUser.username
    } yield Future.successful(
      AuthenticatedUser(definedAccessTokenSubject, username, accessTokenData.roles ++ configUser.roles)
    )
  }

  private def authenticateUserBasedOnProfile(
      accessTokenData: IntrospectedAccessTokenData,
      getProfile: => Future[OidcUserInfo],
      configuration: OAuth2Configuration
  )(implicit ec: ExecutionContext) = {
    getProfile.map { profile =>
      val userIdentity = profile.subject
        .orElse(accessTokenData.subject)
        .getOrElse(throw new IllegalStateException("Missing user identity"))
      val userRoles = profile.roles ++ configuration.getUserRoles(userIdentity)

      val usernameBasedOnUsersConfiguration = configuration.findUserById(userIdentity).flatMap(_.username)

      lazy val usernameBasedOnConfigurationClaim = configuration.usernameClaim match {
        case Some(UsernameClaim.PreferredUsername) =>
          profile.preferredUsername
        case Some(UsernameClaim.GivenName) =>
          profile.givenName
        case Some(UsernameClaim.Nickname) =>
          profile.nickname
        case Some(UsernameClaim.Name) =>
          profile.name
        case _ =>
          None
      }

      val username = usernameBasedOnUsersConfiguration
        .orElse(usernameBasedOnConfigurationClaim)
        .orElse(profile.preferredUsername) // backward compatibility
        .orElse(profile.nickname)          // backward compatibility
        .getOrElse(userIdentity)

      AuthenticatedUser(id = userIdentity, username = username, userRoles)
    }
  }

}
