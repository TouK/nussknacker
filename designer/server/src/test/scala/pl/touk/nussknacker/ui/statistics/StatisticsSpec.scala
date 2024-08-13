package pl.touk.nussknacker.ui.statistics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.StatisticEncryptionSupport

import java.net.URL

class StatisticsSpec extends AnyFunSuite with Matchers {
  private val cfg = StatisticUrlConfig(maybePublicEncryptionKey = None)

  test("should split parameters into 2 URLS") {
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
//    TODO: switch once logstash is ready
//    val cfg = StatisticUrlConfig()
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

  // jak klucz jest niepoprawny to powinniśmy się wywalić, bo w docelowym rozwiązaniu nie przewidujemy, że w ogóle będziemy wysyłać niezaszyfrowanych parametrów
//  test("should return query params if public key is wrong") {
//    val cfg = StatisticUrlConfig(maybePublicEncryptionKey = PublicEncryptionKey(Some("abc")))
//    val sut = new Statistics.NonEmpty(
//      new Fingerprint("t"),
//      new RequestId("req_id"),
//      Map("f" -> "a b", "v" -> "1.6.5-a&b=c")
//    )
//    sut
//      .prepareURLs(cfg)
//      .getOrElse(List.empty)
//      .headOption
//      .map { url =>
//        QueryParamsHelper.extractFromURLString(url.toString)
//      } shouldBe Some(Map("f" -> "a+b", "v" -> "1.6.5-a%26b%3Dc", "fingerprint" -> "t", "req_id" -> "req_id"))
//  }

}
