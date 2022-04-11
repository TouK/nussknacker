package pl.touk.nussknacker.ui.security.oauth2

import io.circe.Decoder
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil

class OpenIdConnectUserInfoSpec extends FunSuite with Matchers {

  test("parses token with role claims") {

    implicit val decoder: Decoder[OpenIdConnectUserInfo] =
      OpenIdConnectUserInfo.decoderWithCustomRolesClaim(Some(List("http://uri1.com", "http://uri2.com")))

    val userInfo = CirceUtil.decodeJsonUnsafe[OpenIdConnectUserInfo](getClass.getResourceAsStream("/oidc-sample1.json").readAllBytes())

    userInfo.roles shouldBe Set("role1", "role2", "role3")
  }

}
