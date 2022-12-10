package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.ListMap

class UsageStatisticsHtmlSnippetTest extends AnyFunSuite with Matchers{

  test("should generate correct url with encoded params") {
    UsageStatisticsHtmlSnippet.prepareUrl(ListMap("f" -> "a b", "v" -> "1.6.5-a&b=c")) shouldBe "https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc"
  }
}
