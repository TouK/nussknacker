package pl.touk.nussknacker.test.retry

import org.scalatest.concurrent.ScalaFutures.PatienceConfig
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

class PatienceConfigToRetryPolicyConverterTest extends AnyFunSuiteLike with Matchers {

  test("should convert to pause policy with total time shorten by input config") {
    forAll(
      Table(
        ("patienceConfig", "expectedAttempts", "expectedInterval"),
        (
          PatienceConfig(Span(10, Seconds), Span(10, Seconds)),
          1,
          10.seconds - PatienceConfigToRetryPolicyConverter.delta
        ),
        (
          PatienceConfig(Span(10, Seconds), Span(1, Seconds)),
          10,
          1.seconds - PatienceConfigToRetryPolicyConverter.delta / 10
        ),
        (PatienceConfig(Span(200, Millis), Span(50, Millis)), 2, 50.millis),
        (PatienceConfig(Span(125, Millis), Span(50, Millis)), 1, 25.millis),
      )
    ) { (patienceConfig, expectedAttempts, expectedInterval) =>
      val (maxAttempts, interval) = PatienceConfigToRetryPolicyConverter.toRetryPolicyInternal(patienceConfig)
      val totalTime               = interval * maxAttempts
      totalTime shouldBe (patienceConfig.timeout.toMillis.millis - PatienceConfigToRetryPolicyConverter.delta)
      (maxAttempts, interval) shouldBe (expectedAttempts, expectedInterval)
    }
  }

}
