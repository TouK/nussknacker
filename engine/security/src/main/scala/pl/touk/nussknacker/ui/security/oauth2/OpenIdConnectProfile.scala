package pl.touk.nussknacker.ui.security.oauth2

import java.time.LocalDate
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import io.circe.java8.time.{JavaTimeDecoders, JavaTimeEncoders}
import pl.touk.nussknacker.ui.security.api.{AuthenticatedUser, LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Profile.getUserRoles

import scala.concurrent.duration.Deadline

@ConfiguredJsonCodec case class OpenIdConnectUserInfo
(
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
  @JsonKey("phone_number") phoneNumber: Option[String], //RFC 3963
  @JsonKey("phone_number_verified") phoneNumberVerified: Option[Boolean],
  address: Option[Map[String, String]],
  @JsonKey("updated_at") updatedAt: Option[Deadline],

  @JsonKey("iss") issuer: Option[String],
  @JsonKey("aud") audition: Option[List[String]],

  // All the following are set only when the userinfo is from an ID token
  @JsonKey("exp") expirationTime: Option[Deadline],
  @JsonKey("iat") issuedAt: Option[Deadline],
  @JsonKey("auth_time") authenticationTime: Option[Deadline]
) extends JwtStandardClaims {
  val jwtId: Option[String] = None
  val notBefore: Option[Deadline] = None
}

object OpenIdConnectUserInfo extends EpochSecondsCodecs with JavaTimeDecoders with JavaTimeEncoders {
  implicit val config: Configuration = Configuration.default
}

object OpenIdConnectProfile extends OAuth2Profile[OpenIdConnectUserInfo] {
  def getAuthenticatedUser(profile: OpenIdConnectUserInfo, configuration: OAuth2Configuration): AuthenticatedUser = {
    val userRoles = getUserRoles(profile.email, configuration)
    val username = profile.preferredUsername.orElse(profile.nickname).orElse(profile.subject).get
    AuthenticatedUser(id = profile.subject.get, username = username, userRoles)
  }
}
