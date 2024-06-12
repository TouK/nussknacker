package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.net.URL

class StatisticsUrlsDomainServiceSpec extends AnyFunSuite with Matchers {

  test("should split parameters into 2 URLS") {
    val threeThousandCharsParam = (1 to 3000).map(_ => "x").mkString

    val sut = new StatisticsUrlsDomainService(
      new Fingerprint("t"),
      Map(
        "q1" -> threeThousandCharsParam,
        "q2" -> threeThousandCharsParam,
        "q3" -> threeThousandCharsParam,
        "q4" -> threeThousandCharsParam,
        "q5" -> threeThousandCharsParam,
      )
    )

    sut.prepareUrls() shouldBe Right(
      List(
        new URL(s"https://stats.nussknacker.io/?q1=$threeThousandCharsParam&q2=$threeThousandCharsParam&fingerprint=t"),
        new URL(s"https://stats.nussknacker.io/?q3=$threeThousandCharsParam&q4=$threeThousandCharsParam&fingerprint=t"),
        new URL(s"https://stats.nussknacker.io/?q5=$threeThousandCharsParam&fingerprint=t")
      )
    )
  }

  test("should generate correct url with encoded params") {
    val sut = new StatisticsUrlsDomainService(new Fingerprint("test"), Map("f" -> "a b", "v" -> "1.6.5-a&b=c"))
    sut.prepareUrls() shouldBe
      Right(List(new URL("https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc&fingerprint=test")))
  }

  test("should return error if the URL cannot be constructed") {
    StatisticsUrlsDomainService.toURL("xd://stats.nussknacker.io/?f=a+b") shouldBe
      Left(CannotGenerateStatisticsError)
  }

}
