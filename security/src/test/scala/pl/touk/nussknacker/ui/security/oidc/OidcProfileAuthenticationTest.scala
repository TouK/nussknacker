package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.security.api.AuthenticatedUser
import pl.touk.nussknacker.ui.security.oauth2.{IntrospectedAccessTokenData, OAuth2Configuration, UsernameClaim}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class OidcProfileAuthenticationTest
    extends AnyFunSuite
    with Matchers
    with TableDrivenPropertyChecks
    with PatientScalaFutures {

  import UsernameClaim._

  private val config = OAuth2Configuration.create(ConfigFactory.parseResources("oidc.conf"))

  test("should create AuthenticatedUser with proper filled username") {
    val adminIdentifier   = "b5a31081-0251-401d-ac76-b375a171a0a3" // se oauth2-users.conf
    val identifier        = UUID.randomUUID().toString
    val preferredUsername = UUID.randomUUID().toString
    val givenName         = UUID.randomUUID().toString
    val nickname          = UUID.randomUUID().toString
    val name              = UUID.randomUUID().toString

    val profile = OidcUserInfo(
      subject = Some(identifier),
      name = Some(name),
      givenName = Some(givenName),
      nickname = Some(nickname),
      preferredUsername = Some(preferredUsername),
      familyName = None,
      middleName = None,
      profile = None,
      picture = None,
      website = None,
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
      (config, profile.copy(preferredUsername = None, nickname = None), expected),
      (config, profile, expected.copy(username = preferredUsername)),                       // back compatibility
      (config, profile.copy(preferredUsername = None), expected.copy(username = nickname)), // back compatibility
      (
        config,
        profile.copy(subject = Some(adminIdentifier)),
        expected.copy(id = adminIdentifier, username = "Adminek")
      ),
      (config.copy(usernameClaim = Some(PreferredUsername)), profile, expected.copy(username = preferredUsername)),
      (config.copy(usernameClaim = Some(GivenName)), profile, expected.copy(username = givenName)),
      (config.copy(usernameClaim = Some(Nickname)), profile, expected.copy(username = nickname)),
      (config.copy(usernameClaim = Some(Name)), profile, expected.copy(username = name)),
    )

    forAll(data) { (config, profile, expected) =>
      val result =
        new OidcProfileAuthentication(config)
          .authenticateUser(IntrospectedAccessTokenData.empty, Future.successful(profile))
          .futureValue
      result shouldBe expected
    }

  }

}
