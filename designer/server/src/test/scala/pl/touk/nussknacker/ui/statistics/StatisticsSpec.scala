package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.StatisticEncryptionSupport

import java.net.URL

class StatisticsSpec extends AnyFunSuite with Matchers {
  private val cfg                     = StatisticUrlConfig(maybePublicEncryptionKey = None)
  private val sizeForQueryParamsNames = "q1=&q2=a".length

  test("should split parameters into a list of URLS") {
    val twoThousandCharsParam = (1 to 2000).map(_ => "x").mkString
    val sut = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map(
        "q1" -> twoThousandCharsParam,
        "q2" -> twoThousandCharsParam,
        "q3" -> twoThousandCharsParam,
        "q4" -> twoThousandCharsParam,
        "q5" -> twoThousandCharsParam,
      )
    )

    sut.prepareURLs(cfg) shouldBe Right(
      List(
        new URL(
          s"https://stats.nussknacker.io/?q1=$twoThousandCharsParam&q2=$twoThousandCharsParam&fingerprint=t&req_id=req_id"
        ),
        new URL(
          s"https://stats.nussknacker.io/?q3=$twoThousandCharsParam&q4=$twoThousandCharsParam&fingerprint=t&req_id=req_id"
        ),
        new URL(s"https://stats.nussknacker.io/?q5=$twoThousandCharsParam&fingerprint=t&req_id=req_id")
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
    val cfg = StatisticUrlConfig(
      maybePublicEncryptionKey = Some(PublicEncryptionKey(StatisticEncryptionSupport.publicKeyForTest))
    )
    val sut = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map("f" -> "a b", "v" -> "1.6.5-a&b=c")
    )
    sut
      .prepareURLs(cfg)
      .getOrElse(List.empty)
      .headOption
      .map { url =>
        StatisticEncryptionSupport.decode(url.toString)
      } shouldBe Some(Map("f" -> "a+b", "v" -> "1.6.5-a%26b%3Dc", "fingerprint" -> "t", "req_id" -> "req_id"))
  }

  test("should not split links if below the limit") {
    val cfg = StatisticUrlConfig(
      maybePublicEncryptionKey = Some(PublicEncryptionKey(StatisticEncryptionSupport.publicKeyForTest))
    )
    val almostLimitLongParam = (1 to cfg.urlBytesSizeLimit - sizeForQueryParamsNames).map(_ => "x").mkString
    val urls = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map("q1" -> almostLimitLongParam, "q2" -> "a")
    ).prepareURLs(cfg)

    urls
      .getOrElse(List.empty)
      .length shouldBe 1

    urls
      .getOrElse(List.empty)
      .head
      .toString
      .length should be < 7000
  }

}
