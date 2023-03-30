package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import java.time.ZoneId
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class DailyWindowsOffsetDependingOnTimezoneSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("should return no offset for non-daily window") {
    DailyWindowsOffsetDependingOnTimezone.offset(Duration(1, TimeUnit.HOURS), ZoneId.of("GMT+1")) shouldBe None
  }

  test("should return correct offset for daily window") {
    forAll(Table(
      ("zoneId", "offset"),
      ("UTC", Duration.Zero),
      /** Custom timezone must start with GMT, see {@link java.util.TimeZone} */
      ("UTC+1", Duration.Zero),
      ("GMT", Duration.Zero),
      ("GMT+1", Duration(-1, TimeUnit.HOURS)),
      ("GMT-01:15", Duration(1, TimeUnit.HOURS).plus(Duration(15, TimeUnit.MINUTES))),
      ("Europe/Warsaw", Duration(-1, TimeUnit.HOURS)),
      ("CET", Duration(-1, TimeUnit.HOURS)),
      ("Pacific/Chatham", Duration(-12, TimeUnit.HOURS).minus(Duration(45, TimeUnit.MINUTES))),

    )) { (zoneId, offset) =>
      DailyWindowsOffsetDependingOnTimezone.offset(Duration(2, TimeUnit.DAYS), ZoneId.of(zoneId)) shouldBe Some(offset)
    }

  }
}
