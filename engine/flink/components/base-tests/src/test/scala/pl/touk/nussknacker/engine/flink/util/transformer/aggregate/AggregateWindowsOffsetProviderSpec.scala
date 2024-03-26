package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class AggregateWindowsOffsetProviderSpec extends AnyFunSuite with Matchers {

  test("should return the same offset as expected if window is longer") {
    AggregateWindowsOffsetProvider.offset(
      windowDuration = Duration.apply(30, TimeUnit.MINUTES),
      expectedOffset = Duration.apply(20, TimeUnit.MINUTES),
    ) shouldBe Duration.apply(20, TimeUnit.MINUTES)
  }

  test("should return offset 0 if expected offset is equal to window length") {
    AggregateWindowsOffsetProvider.offset(
      windowDuration = Duration.apply(20, TimeUnit.MINUTES),
      expectedOffset = Duration.apply(20, TimeUnit.MINUTES),
    ) shouldBe Duration.Zero
  }

  test("should handle offset bigger than window") {
    AggregateWindowsOffsetProvider.offset(
      windowDuration = Duration.apply(20, TimeUnit.MINUTES),
      expectedOffset = Duration.apply(30, TimeUnit.MINUTES),
    ) shouldBe Duration.apply(10, TimeUnit.MINUTES)
  }

}
