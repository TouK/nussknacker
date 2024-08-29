package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.StatisticEncryptionSupport

class StatisticsSpec extends AnyFunSuite with Matchers with EitherValues {
  private val cfg                     = StatisticUrlConfig(publicEncryptionKey = StatisticEncryptionSupport.publicKey)
  private val sizeForQueryParamsNames = "q1=&q2=a".length
  private val maxUrlLength            = 7000

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

    sut
      .prepareURLs(cfg)
      .value
      .map(url => StatisticEncryptionSupport.decodeToString(url.toString)) shouldBe List(
      s"q1=$twoThousandCharsParam&q2=$twoThousandCharsParam&fingerprint=t&req_id=req_id",
      s"q3=$twoThousandCharsParam&q4=$twoThousandCharsParam&fingerprint=t&req_id=req_id",
      s"q5=$twoThousandCharsParam&fingerprint=t&req_id=req_id"
    )
  }

  test("should generate correct url with encoded params") {
    val sut = new Statistics.NonEmpty(
      new Fingerprint("t"),
      new RequestId("req_id"),
      Map("f" -> "a b", "v" -> "1.6.5-a&b=c")
    )
    sut
      .prepareURLs(cfg)
      .value
      .map(url => StatisticEncryptionSupport.decodeToString(url.toString)) shouldBe List(
      s"f=a+b&v=1.6.5-a%26b%3Dc&fingerprint=t&req_id=req_id"
    )
  }

  test("should return error if the URL cannot be constructed") {
    Statistics.toURL("xd://stats.nussknacker.io/?f=a+b") shouldBe Left(CannotGenerateStatisticsError)
  }

  test("should return encrypted URL if not set otherwise") {
    val cfg = StatisticUrlConfig(publicEncryptionKey = StatisticEncryptionSupport.publicKey)
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
    val cfg                  = StatisticUrlConfig(publicEncryptionKey = StatisticEncryptionSupport.publicKey)
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
      .length should be < maxUrlLength
  }

}
