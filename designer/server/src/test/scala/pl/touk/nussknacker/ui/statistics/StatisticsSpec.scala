package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.hamcrest.Matchers.{equalTo, matchesRegex}

import java.net.URL

class StatisticsSpec extends AnyFunSuite with Matchers {
  private val cfg = StatisticUrlConfig(encryptQueryParams = false)

  test("should split parameters into 2 URLS") {
    val threeThousandCharsParam = (1 to 3000).map(_ => "x").mkString
    val sut = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map(
        "q1" -> threeThousandCharsParam,
        "q2" -> threeThousandCharsParam,
        "q3" -> threeThousandCharsParam,
        "q4" -> threeThousandCharsParam,
        "q5" -> threeThousandCharsParam,
      )
    )

    sut.prepareURLs(cfg) shouldBe Right(
      List(
        new URL(
          s"https://stats.nussknacker.io/?q1=$threeThousandCharsParam&q2=$threeThousandCharsParam&fingerprint=t&req_id=req_id"
        ),
        new URL(
          s"https://stats.nussknacker.io/?q3=$threeThousandCharsParam&q4=$threeThousandCharsParam&fingerprint=t&req_id=req_id"
        ),
        new URL(s"https://stats.nussknacker.io/?q5=$threeThousandCharsParam&fingerprint=t&req_id=req_id")
      )
    )
  }

  test("should generate correct url with encoded params") {
    val sut = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map("f" -> "a b", "v" -> "1.6.5-a&b=c")
    )
    sut.prepareURLs(cfg) shouldBe Right(
      List(
        new URL("https://stats.nussknacker.io/?f=a+b&v=1.6.5-a%26b%3Dc&fingerprint=t&req_id=req_id")
      )
    )
  }

  test("should return error if the URL cannot be constructed") {
    Statistics.toURL("xd://stats.nussknacker.io/?f=a+b") shouldBe Left(CannotGenerateStatisticsError)
  }

  test("should return encrypted URL if not set otherwise") {
    val cfg = StatisticUrlConfig()
    val sut = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map("f" -> "a b", "v" -> "1.6.5-a&b=c")
    )
    sut.prepareURLs(cfg).getOrElse(List.empty).map { url =>
      url.toString matches s"https://stats.nussknacker.io/\\?encrypted=\\S+key=\\S+" shouldBe true
    }
  }

}
