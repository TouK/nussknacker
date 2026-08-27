package pl.touk.nussknacker.ui.ha

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class HaModeSpec extends AnyFunSuite with Matchers {

  private def parse(config: String): HaMode = HaMode.fromConfig(ConfigFactory.parseString(config))

  test("periodicLockMode defaults to dedicated when not configured") {
    val mode = parse("""ha { enabled: true, instanceId: "node-1" }""")

    mode shouldBe a[HaMode.Enabled]
    mode.asInstanceOf[HaMode.Enabled].periodicLockMode shouldBe HaMode.PeriodicLockMode.Dedicated
  }

  test("periodicLockMode is read from config") {
    val mode = parse("""ha { enabled: true, instanceId: "node-1", periodicLockMode: leader }""")

    mode.asInstanceOf[HaMode.Enabled].periodicLockMode shouldBe HaMode.PeriodicLockMode.Leader
  }

  test("unknown periodicLockMode fails config parsing") {
    a[Exception] should be thrownBy parse(
      """ha { enabled: true, instanceId: "node-1", periodicLockMode: whatever }"""
    )
  }

  test("periodicLockMode is not read when ha is disabled") {
    parse("""ha { enabled: false, instanceId: "node-1", periodicLockMode: whatever }""") shouldBe HaMode.Disabled(
      "node-1"
    )
  }

}
