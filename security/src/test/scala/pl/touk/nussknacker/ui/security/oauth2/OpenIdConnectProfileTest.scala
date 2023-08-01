package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser

import java.util.UUID

class OpenIdConnectProfileTest extends AnyFunSuite with Matchers  with TableDrivenPropertyChecks {

  import UsernameFieldName._

  private val config = OAuth2Configuration.create(ConfigFactory.parseResources("oidc.conf"))

  test("should create AuthenticatedUser with proper filled username") {
    val adminIdentifier = "b5a31081-0251-401d-ac76-b375a171a0a3" //se oauth2-users.conf
    val identifier = UUID.randomUUID().toString
    val preferredUsername = UUID.randomUUID().toString
    val givenName = UUID.randomUUID().toString
    val nickname = UUID.randomUUID().toString
    val name = UUID.randomUUID().toString

    val profile = OpenIdConnectUserInfo(
      subject = Some(identifier),
      name = Some(name),
      givenName = Some(givenName),
      nickname = Some(nickname),
      preferredUsername = Some(preferredUsername),
      familyName = None,
      middleName = None,
      profile = None,
      picture = None,
      website =None,
      email = None,
      emailVerified = None,
      gender = None,
      birthdate = None,
      zoneinfo = None,
      locale = None,
      phoneNumber = None,
      phoneNumberVerified = None,
      address = None,
      updatedAt = None,
      issuer = None,
      audience = None,
      expirationTime = None,
      issuedAt = None,
      authenticationTime = None,
      roles = Set.empty
    )

    val expected = AuthenticatedUser(id = identifier, username = identifier, roles = Set.empty)

    val data = Table(
      ("config", "profile", "result"),
      (config, profile, expected),
      (config, profile.copy(subject = Some(adminIdentifier)), expected.copy(id = adminIdentifier, username = "Adminek")),
      (config.copy(usernameFieldName = Some(PreferredUsername)), profile, expected.copy(username = preferredUsername)),
      (config.copy(usernameFieldName = Some(GivenName)), profile, expected.copy(username = givenName)),
      (config.copy(usernameFieldName = Some(Nickname)), profile, expected.copy(username = nickname)),
      (config.copy(usernameFieldName = Some(Name)), profile, expected.copy(username = name)),
    )

    forAll(data){ (config, profile,  expected) =>
      val result = OpenIdConnectProfile.getAuthenticatedUser(profile, config)
      result shouldBe expected
    }

  }
}
