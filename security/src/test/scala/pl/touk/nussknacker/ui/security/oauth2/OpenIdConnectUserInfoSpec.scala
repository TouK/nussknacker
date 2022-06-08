package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil

class OpenIdConnectUserInfoSpec extends FunSuite with Matchers {

  test("should add roles based on role claims when claims match") {

    implicit val decoder: Decoder[OpenIdConnectUserInfo] =
      OpenIdConnectUserInfo.decoderWithCustomRolesClaim(Some(List("http://uri1.com", "http://uri2.com")))

    val userInfo = CirceUtil.decodeJsonUnsafe[OpenIdConnectUserInfo](getClass.getResourceAsStream("/oidc-sample1.json").readAllBytes())

    userInfo.roles shouldBe Set("role1", "role2", "role3")
  }

  test("should return empty roles when any claim matches") {

    implicit val decoder: Decoder[OpenIdConnectUserInfo] =
      OpenIdConnectUserInfo.decoderWithCustomRolesClaim(Some(List("http://notexistingclaim.com")))

    val userInfo = CirceUtil.decodeJsonUnsafe[OpenIdConnectUserInfo](getClass.getResourceAsStream("/oidc-sample1.json").readAllBytes())

    userInfo.roles shouldBe Set()
  }

}
