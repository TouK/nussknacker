package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.{Table, forAll}
import org.scalatest.prop.TableFor2
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.build.ScenarioBuilder

class IdValidatorTest extends AnyFunSuite with Matchers {

  object TestCases {

    val scenarioIdErrorCases: TableFor2[String, List[ScenarioPropertiesError]] = Table(
      ("scenarioId", "errors"),
      ("", List(EmptyScenarioId(false))),
      ("  ", List(BlankScenarioId(false))),
      ("trailingSpace ", List(TrailingSpacesScenarioId(false))),
      (" leadingSpace", List(LeadingSpacesScenarioId(false))),
      (
        " leadingAndTrailingSpace ",
        List(LeadingSpacesScenarioId(false), TrailingSpacesScenarioId(false))
      ),
    )

    val nodeIdErrorCases: TableFor2[String, List[NodeIdError]] = Table(
      ("nodeId", "errors"),
      ("", List(EmptyNodeId)),
      ("  ", List(BlankNodeId("  "))),
      ("trailingSpace ", List(TrailingSpacesNodeId("trailingSpace "))),
      (" leadingSpace", List(LeadingSpacesNodeId(" leadingSpace"))),
      (
        " leadingAndTrailingSpace ",
        List(LeadingSpacesNodeId(" leadingAndTrailingSpace "), TrailingSpacesNodeId(" leadingAndTrailingSpace "))
      ),
    )

  }

  test("should validate happy path") {
    val scenario = validScenario("validId")
    IdValidator.validate(scenario).isValid shouldBe true
  }

  test("should validate both scenario and node id") {
    val scenario = validScenario("", "")
    IdValidator.validate(scenario) match {
      case Validated.Invalid(errors) =>
        errors.toList should contain theSameElementsAs List(EmptyScenarioId(false), EmptyNodeId)
      case Validated.Valid(_) =>
        fail("Validation succeeded, but was expected to fail")
    }
  }

  test("should handle all cases of scenario id validation") {
    forAll(TestCases.scenarioIdErrorCases) { (scenarioId: String, expectedErrors: List[ScenarioPropertiesError]) =>
      {
        IdValidator.validate(validScenario(scenarioId)) match {
          case Validated.Invalid(errors) =>
            errors.toList should contain theSameElementsAs expectedErrors
          case Validated.Valid(_) =>
            fail("Validation succeeded, but was expected to fail")
        }
      }
    }
  }

  test("should handle all cases of node id validation") {
    forAll(TestCases.nodeIdErrorCases) { (nodeId: String, expectedErrors: List[NodeIdError]) =>
      {
        IdValidator.validate(validScenario(nodeId = nodeId)) match {
          case Validated.Invalid(errors) =>
            errors.toList should contain theSameElementsAs expectedErrors
          case Validated.Valid(_) =>
            fail("Validation succeeded, but was expected to fail")
        }
      }
    }
  }

  private def validScenario(id: String = "id", nodeId: String = "nodeId") =
    ScenarioBuilder
      .streaming(id)
      .source(nodeId, "test")
      .emptySink("sinkId", "test")

}
