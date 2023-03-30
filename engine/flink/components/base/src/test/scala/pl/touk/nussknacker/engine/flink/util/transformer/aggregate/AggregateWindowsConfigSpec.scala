package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import java.time.zone.ZoneRulesException

class AggregateWindowsConfigSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("should read only valid config") {
    forAll(Table(
      ("config", "isValid"),
      ("UTC", true),
      ("UTC+1", true),
      ("UTC-01", true),
      ("GMT+00", true),
      ("GMT+01", true),
      ("GMT+11", true),
      ("America/Montreal", true),
      ("CEST", false),
      ("CET", true),
      ("Europe/Warsaw", true),
      ("Warsaw", false),

    )) { (configValue, valid) =>
      val c = ConfigFactory.empty().withValue("aggregateWindowsConfig.dailyWindowsAlignZoneId", fromAnyRef(configValue))
      if (valid)
        noException should be thrownBy AggregateWindowsConfig.loadOrDefault(c)
      else
        an[ZoneRulesException] should be thrownBy AggregateWindowsConfig.loadOrDefault(c)
    }
  }

  test("should load default when no config") {
    AggregateWindowsConfig.loadOrDefault(ConfigFactory.empty()) shouldBe AggregateWindowsConfig.Default
  }
}
