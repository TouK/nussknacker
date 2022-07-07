package pl.touk.nussknacker.ui.security.oauth2

import io.circe.{Decoder, Encoder, Json}

import java.time.{Instant, LocalDate}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Profile.getUserRoles

import scala.concurrent.duration.Deadline

@ConfiguredJsonCodec(decodeOnly = true) case class OpenIdConnectUserInfo
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
  val jwtId: Option[String] = None
  val notBefore: Option[Instant] = None
}

object OpenIdConnectUserInfo extends EitherCodecs with EpochSecondsCodecs {
  implicit val config: Configuration = Configuration.default.withDefaults

  lazy val decoder: Decoder[OpenIdConnectUserInfo] = deriveConfiguredDecoder[OpenIdConnectUserInfo]
  def decoderWithCustomRolesClaim(rolesClaims: List[String]): Decoder[OpenIdConnectUserInfo] =
    decoder.prepare {
      _.withFocus(_.mapObject { jsonObject =>
        val allRoles = rolesClaims.flatMap { roleClaim =>
          jsonObject.apply(roleClaim).flatMap(_.asArray)
        }.flatten
        jsonObject.add("roles", Json.fromValues(allRoles))
      })
    }
  def decoderWithCustomRolesClaim(rolesClaims: Option[List[String]]): Decoder[OpenIdConnectUserInfo] = rolesClaims.map(decoderWithCustomRolesClaim).getOrElse(decoder)
}

object OpenIdConnectProfile extends OAuth2Profile[OpenIdConnectUserInfo] {
  def getAuthenticatedUser(profile: OpenIdConnectUserInfo, configuration: OAuth2Configuration): AuthenticatedUser = {
    val userRoles =
      profile.roles ++
      getUserRoles(profile.subject.get, configuration)
    val username = profile.preferredUsername.orElse(profile.nickname).orElse(profile.subject).get
    AuthenticatedUser(id = profile.subject.get, username = username, userRoles)
  }
}
