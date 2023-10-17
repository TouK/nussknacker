package pl.touk.nussknacker.ui.security.oidc

import io.circe.Decoder
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil

class OidcUserInfoSpec extends AnyFunSuite with Matchers {

  test("parses token with role claims") {

    implicit val decoder: Decoder[OidcUserInfo] =
      OidcUserInfo.decoderWithCustomRolesClaim(Some(List("http://uri1.com", "http://uri2.com")))

    val userInfo = CirceUtil.decodeJsonUnsafe[OidcUserInfo](
      getClass.getResourceAsStream("/oidc-sample1.json").readAllBytes()
    )

    userInfo.roles shouldBe Set("role1", "role2", "role3")
  }

}
