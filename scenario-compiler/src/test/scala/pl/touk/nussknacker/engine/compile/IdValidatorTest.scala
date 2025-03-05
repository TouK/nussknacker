package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.{forAll, Table}
import org.scalatest.prop.TableFor2
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class IdValidatorTest extends AnyFunSuite with Matchers {

  test("should handle all cases of scenario id validation") {
    forAll(IdValidationTestData.scenarioIdErrorCases) {
      (scenarioId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          IdValidator.validate(validScenario(scenarioId), isFragment = false) match {
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
          IdValidator.validate(validFragment(scenarioId), isFragment = true) match {
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
        IdValidator.validate(validScenario(nodeId = nodeId), isFragment = true) match {
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
    IdValidator.validate(scenarioWithEmptyIds, isFragment = false) match {
      case Validated.Invalid(errors) =>
        errors.toList should contain theSameElementsAs List(
          ScenarioNameError(EmptyValue, ProcessName(""), isFragment = false),
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
      ("", List(ScenarioNameError(EmptyValue, ProcessName(""), forFragment))),
      (" ", List(ScenarioNameError(BlankId, ProcessName(" "), forFragment))),
      ("trailingSpace ", List(ScenarioNameError(TrailingSpacesId, ProcessName("trailingSpace "), forFragment))),
      (" leadingSpace", List(ScenarioNameError(LeadingSpacesId, ProcessName(" leadingSpace"), forFragment))),
      (
        " leadingAndTrailingSpace ",
        List(
          ScenarioNameError(LeadingSpacesId, ProcessName(" leadingAndTrailingSpace "), forFragment),
          ScenarioNameError(TrailingSpacesId, ProcessName(" leadingAndTrailingSpace "), forFragment)
        )
      ),
    )
  }

}
