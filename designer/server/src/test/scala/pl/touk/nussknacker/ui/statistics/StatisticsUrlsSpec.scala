package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StatisticsUrlsSpec extends AnyFunSuite with Matchers {
  val sut = new StatisticsUrls(StatisticUrlConfig())

  test("should split parameters into 2 URLS") {
    val threeThousandCharsParam = (1 to 3000).map(_ => "x").mkString

    sut.prepare(
      new Fingerprint("t"),
      new CorrelationId("cor_id"),
      Map(
        "q1" -> threeThousandCharsParam,
        "q2" -> threeThousandCharsParam,
        "q3" -> threeThousandCharsParam,
        "q4" -> threeThousandCharsParam,
        "q5" -> threeThousandCharsParam,
      )
    ) shouldBe List(
      s"https://stats.nussknacker.io/?q1=$threeThousandCharsParam&q2=$threeThousandCharsParam&fingerprint=t&co_id=cor_id",
      s"https://stats.nussknacker.io/?q3=$threeThousandCharsParam&q4=$threeThousandCharsParam&fingerprint=t&co_id=cor_id",
      s"https://stats.nussknacker.io/?q5=$threeThousandCharsParam&fingerprint=t&co_id=cor_id"
    )
  }

  test("should generate correct url with encoded params") {
    sut.prepare(
      new Fingerprint("test"),
      new CorrelationId("cor_id"),
      Map("f" -> "a b", "v" -> "1.6.5-a&b=c")
    ) shouldBe List("https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc&fingerprint=test&co_id=cor_id")
  }

}
