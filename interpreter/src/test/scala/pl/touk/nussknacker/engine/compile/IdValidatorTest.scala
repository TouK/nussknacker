package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.{Table, forAll}
import org.scalatest.prop.TableFor2
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class IdValidatorTest extends AnyFunSuite with Matchers {

  test("should handle all cases of scenario id validation") {
    forAll(IdValidationTestData.scenarioIdErrorCases) {
      (scenarioId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          IdValidator.validate(validScenario(scenarioId)) match {
            case Validated.Invalid(errors) =>
              errors.toList shouldBe expectedErrors
            case Validated.Valid(_) =>
              expectedErrors shouldBe empty
          }
        }
    }
  }

  test("should handle all cases of fragment id validation") {
    forAll(IdValidationTestData.fragmentIdErrorCases) {
      (scenarioId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          IdValidator.validate(validFragment(scenarioId)) match {
            case Validated.Invalid(errors) =>
              errors.toList shouldBe expectedErrors
            case Validated.Valid(_) =>
              expectedErrors shouldBe empty
          }
        }
    }
  }

  test("should handle all cases of node id validation") {
    forAll(IdValidationTestData.nodeIdErrorCases) { (nodeId: String, expectedErrors: List[ProcessCompilationError]) =>
      {
        IdValidator.validate(validScenario(nodeId = nodeId)) match {
          case Validated.Invalid(errors) =>
            errors.toList shouldBe expectedErrors
          case Validated.Valid(_) =>
            expectedErrors shouldBe empty
        }
      }
    }
  }

  test("should validate both scenario and node id") {
    val scenarioWithEmptyIds = validScenario("", "")
    IdValidator.validate(scenarioWithEmptyIds) match {
      case Validated.Invalid(errors) =>
        errors.toList should contain theSameElementsAs List(
          ScenarioIdError(EmptyValue, "", isFragment = false),
          NodeIdValidationError(EmptyValue, "")
        )
      case Validated.Valid(_) =>
        fail("Validation succeeded, but was expected to fail")
    }
  }

  private def validScenario(id: String = "id", nodeId: String = "nodeId"): CanonicalProcess =
    ScenarioBuilder
      .streaming(id)
      .source(nodeId, "source")
      .emptySink("sinkId", "sink")

  private def validFragment(id: String): CanonicalProcess =
    ScenarioBuilder.fragmentWithInputNodeId(id, "input").emptySink("sinkId", "test")

}

object IdValidationTestData {

  val nodeIdErrorCases: TableFor2[String, List[IdError]] = Table(
    ("nodeId", "errors"),
    ("validId", List.empty),
    ("", List(NodeIdValidationError(EmptyValue, ""))),
    (" ", List(NodeIdValidationError(BlankId, " "))),
    ("trailingSpace ", List(NodeIdValidationError(TrailingSpacesId, "trailingSpace "))),
    (" leadingSpace", List(NodeIdValidationError(LeadingSpacesId, " leadingSpace"))),
    (
      " leadingAndTrailingSpace ",
      List(
        NodeIdValidationError(LeadingSpacesId, " leadingAndTrailingSpace "),
        NodeIdValidationError(TrailingSpacesId, " leadingAndTrailingSpace ")
      )
    ),
  )

  val scenarioIdErrorCases: TableFor2[String, List[IdError]] = buildProcessIdErrorCases(false)
  val fragmentIdErrorCases: TableFor2[String, List[IdError]] = buildProcessIdErrorCases(true)

  private def buildProcessIdErrorCases(forFragment: Boolean): TableFor2[String, List[IdError]] = {
    Table(
      ("scenarioId", "errors"),
      ("validId", List.empty),
      ("", List(ScenarioIdError(EmptyValue, "", forFragment))),
      (" ", List(ScenarioIdError(BlankId, " ", forFragment))),
      ("trailingSpace ", List(ScenarioIdError(TrailingSpacesId, "trailingSpace ", forFragment))),
      (" leadingSpace", List(ScenarioIdError(LeadingSpacesId, " leadingSpace", forFragment))),
      (
        " leadingAndTrailingSpace ",
        List(
          ScenarioIdError(LeadingSpacesId, " leadingAndTrailingSpace ", forFragment),
          ScenarioIdError(TrailingSpacesId, " leadingAndTrailingSpace ", forFragment)
        )
      ),
    )
  }

}
