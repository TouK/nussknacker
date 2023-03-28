package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class DailyWindowDetectorSpec extends AnyFunSuite with Matchers {

  test("window 10m - is not daily window") {
    DailyWindowDetector.isDailyWindow(Duration(10, TimeUnit.MINUTES)) shouldBe false
  }

  test("window 1h - is not daily window") {
    DailyWindowDetector.isDailyWindow(Duration(1, TimeUnit.HOURS)) shouldBe false
  }

  test("window 10d - is daily window") {
    DailyWindowDetector.isDailyWindow(Duration(10, TimeUnit.DAYS)) shouldBe true
  }

  test("window 24h - is daily window") {
    DailyWindowDetector.isDailyWindow(Duration(24, TimeUnit.HOURS)) shouldBe true
  }

  test("window 25h - is not daily window") {
    DailyWindowDetector.isDailyWindow(Duration(25, TimeUnit.HOURS)) shouldBe false
  }
}
