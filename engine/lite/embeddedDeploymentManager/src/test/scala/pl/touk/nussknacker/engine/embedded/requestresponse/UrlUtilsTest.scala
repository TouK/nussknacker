package pl.touk.nussknacker.engine.embedded.requestresponse

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.lite.requestresponse.UrlUtils

class UrlUtilsTest extends AnyFunSuite with Matchers {

  test("slug sanitization") {
    UrlUtils.sanitizeUrlSlug("foo") shouldEqual "foo"
    UrlUtils.sanitizeUrlSlug("foo-._~") shouldEqual "foo-._~"
    UrlUtils.sanitizeUrlSlug("foo żółć") shouldEqual "foo-----"
    UrlUtils.sanitizeUrlSlug("%%%") shouldEqual "---"
  }

  test("slug validation") {
    UrlUtils.validateUrlSlug("foo") shouldBe true
    UrlUtils.validateUrlSlug("foo-._~") shouldBe true
    UrlUtils.validateUrlSlug("foo%") shouldBe false
    UrlUtils.validateUrlSlug("foo bar") shouldBe false
  }

}
