package pl.touk.nussknacker.engine.aws.managedflink

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName

class FlinkApplicationNameSpec extends AnyFunSuite with Matchers {

  import FlinkApplicationName.{FlinkApplicationNameOps, ScenarioNameOps}

  test("should allow spaces in the middle and convert them to underscores") {
    ProcessName("test scenario-1").toFlinkApplicationName shouldBe FlinkApplicationName("test_scenario-1")
    ProcessName("test  scenario-1").toFlinkApplicationName shouldBe FlinkApplicationName("test__scenario-1")
    ProcessName("test scenario name").toFlinkApplicationName shouldBe FlinkApplicationName("test_scenario_name")
  }

  test("should reject leading or trailing spaces") {
    an[IllegalArgumentException] shouldBe thrownBy(ProcessName(" test").toFlinkApplicationName)
    an[IllegalArgumentException] shouldBe thrownBy(ProcessName("test ").toFlinkApplicationName)
  }

  test("should reject characters outside digits letters hyphen and spaces") {
    an[IllegalArgumentException] shouldBe thrownBy(ProcessName("test_scenario").toFlinkApplicationName)
    an[IllegalArgumentException] shouldBe thrownBy(ProcessName("test.scenario").toFlinkApplicationName)
  }

  test("should decode underscores back to spaces") {
    FlinkApplicationName("test_scenario-1").toProcessName shouldBe ProcessName("test scenario-1")
    FlinkApplicationName("test__scenario-1").toProcessName shouldBe ProcessName("test  scenario-1")
    FlinkApplicationName("test_scenario_name").toProcessName shouldBe ProcessName("test scenario name")
  }

  test("should preserve process name after encode decode round-trip") {
    val processNames = List(
      ProcessName("test"),
      ProcessName("test scenario"),
      ProcessName("test  scenario-1"),
      ProcessName("test scenario name")
    )

    processNames.foreach { processName =>
      processName.toFlinkApplicationName.toProcessName shouldBe processName
    }
  }

}
