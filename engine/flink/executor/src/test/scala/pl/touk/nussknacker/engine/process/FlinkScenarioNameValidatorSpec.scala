package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class FlinkScenarioNameValidatorSpec extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {
  private lazy val validator = new FlinkScenarioNameValidator(ConfigFactory.empty())

  it should "pass valid names" in {
    forAll(Table(
      "scenario name",
      "valid name",
      "a",
      "a a",
      "1",
      "_",
      "-",
    )) { scenarioName =>
      validateScenarioName(scenarioName).isValid shouldBe true
    }
  }

  it should "reject invalid names" in {
    forAll(Table(
      "scenario name",
      " ",
      "",
      " a",
      "a ",
      " a ",
      "ą",
      "abc+",
      "¹",
      "０",
      "あ",
      "🚀",
    )) { scenarioName =>
      validateScenarioName(scenarioName).isValid shouldBe false
    }
  }

  private def validateScenarioName(scenarioName: String) =
    validator.validate(CanonicalProcess(MetaData(scenarioName, LiteStreamMetaData()), List.empty))
}
