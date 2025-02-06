package pl.touk.nussknacker.engine.flink.minicluster.util

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.concurrent.duration._

class DurationToRetryPolicyConverterTest extends AnyFunSuiteLike with Matchers {

  test("should convert duration to pause policy") {
    forAll(
      Table(
        ("totalTime", "interval", "expectedMaxAttempts", "expectedCorrectedInterval"),
        (10.seconds, 10.seconds, 1, 10.seconds),
        (10.seconds, 1.second, 10, 1.seconds),
        (200.millis, 50.millis, 4, 50.millis),
        (500.millis, 1.second, 1, 500.millis),
        (0.seconds, 1.second, 0, 0.seconds),
        (-1.seconds, 1.second, 0, 0.seconds),
      )
    ) { (totalTime, interval, expectedMaxAttempts, expectedCorrectedInterval) =>
      val (maxAttempts, correctedInterval) = DurationToRetryPolicyConverter.toPausePolicyInternal(totalTime, interval)
      val totalTimeAfterCorrection         = correctedInterval * maxAttempts
      if (totalTime >= 0.millis) {
        totalTimeAfterCorrection shouldBe totalTime
      }
      (maxAttempts, correctedInterval) shouldBe (expectedMaxAttempts, expectedCorrectedInterval)
    }
  }

}
