package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UsageStatisticsUrlTest extends AnyFunSuite with Matchers{

  test("should generate correct url with encoded params") {
    UsageStatisticsUrl("a b", "1.6.5-a&b=c") shouldBe "https://stats.nussknacker.io/?fingerprint=a+b&version=1.6.5-a%26b%3Dc"
  }
}
