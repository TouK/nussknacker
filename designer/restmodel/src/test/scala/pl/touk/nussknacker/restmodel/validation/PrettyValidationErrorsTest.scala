package pl.touk.nussknacker.restmodel.validation

import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableFor3
import org.scalatest.prop.TableDrivenPropertyChecks.{Table, forAll}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  BlankId,
  EmptyValue,
  IdError,
  IllegalCharactersId,
  LeadingSpacesId,
  NodeIdValidationError,
  ScenarioIdError,
  TrailingSpacesId
}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationErrorType

class PrettyValidationErrorsTest extends AnyFunSuite with Matchers with Inside {

  test("should properly map id errors") {
    forAll(IdErrorTestData.idErrorsWithMessages) {
      (error: IdError, expectedMessage: String, expectedDescription: String) =>
        val formattedError = PrettyValidationErrors.formatErrorMessage(error)
        formattedError.message shouldBe expectedMessage
        formattedError.description shouldBe expectedDescription
    }
  }

  test("should properly map id error type") {
    IdErrorTestData.allIdErrors.foreach { error =>
      val formattedErrorType = PrettyValidationErrors.formatErrorMessage(error).errorType
      error match {
        case NodeIdValidationError(errorType, _) =>
          errorType match {
            case ProcessCompilationError.EmptyValue | ProcessCompilationError.IllegalCharactersId(_) =>
              formattedErrorType shouldBe NodeValidationErrorType.RenderNotAllowed
            case _ => formattedErrorType shouldBe NodeValidationErrorType.SaveAllowed
          }
        case ScenarioIdError(_, _, _) => formattedErrorType shouldBe NodeValidationErrorType.SaveAllowed
      }
    }
  }

}

object IdErrorTestData {

  val emptyIdScenarioError: ScenarioIdError   = ScenarioIdError(EmptyValue, "", isFragment = false)
  val emptyIdFragmentError: ScenarioIdError   = ScenarioIdError(EmptyValue, "", isFragment = true)
  val emptyIdNodeError: NodeIdValidationError = NodeIdValidationError(EmptyValue, "")

  val blankIdScenarioError: ScenarioIdError   = ScenarioIdError(BlankId, " ", isFragment = false)
  val blankIdFragmentError: ScenarioIdError   = ScenarioIdError(BlankId, " ", isFragment = true)
  val blankIdNodeError: NodeIdValidationError = NodeIdValidationError(BlankId, " ")

  val leadingSpacesIdScenarioError: ScenarioIdError =
    ScenarioIdError(LeadingSpacesId, " leadingSpace", isFragment = false)
  val leadingSpacesIdFragmentError: ScenarioIdError =
    ScenarioIdError(LeadingSpacesId, " leadingSpace", isFragment = true)
  val leadingSpacesIdNodeError: NodeIdValidationError = NodeIdValidationError(LeadingSpacesId, " leadingSpace")

  val trailingSpacesIdScenarioError: ScenarioIdError =
    ScenarioIdError(TrailingSpacesId, "trailingSpace ", isFragment = false)
  val trailingSpacesIdFragmentError: ScenarioIdError =
    ScenarioIdError(TrailingSpacesId, "trailingSpace ", isFragment = true)
  val trailingSpacesIdNodeError: NodeIdValidationError = NodeIdValidationError(TrailingSpacesId, "trailingSpace ")

  val illegalCharsReadable = "Some illegal character (x), another illegal character (!)"
  val illegalCharactersIdScenarioError: ScenarioIdError =
    ScenarioIdError(IllegalCharactersId(illegalCharsReadable), "idWithIllegalChars!", isFragment = false)
  val illegalCharactersIdNodeError: NodeIdValidationError =
    NodeIdValidationError(IllegalCharactersId(illegalCharsReadable), "idWithIllegalChars!")

  val allIdErrors: Set[IdError] =
    Set(
      emptyIdScenarioError,
      emptyIdFragmentError,
      emptyIdNodeError,
      blankIdScenarioError,
      blankIdFragmentError,
      blankIdNodeError,
      leadingSpacesIdScenarioError,
      leadingSpacesIdFragmentError,
      leadingSpacesIdNodeError,
      trailingSpacesIdScenarioError,
      trailingSpacesIdFragmentError,
      trailingSpacesIdNodeError,
      illegalCharactersIdScenarioError,
      illegalCharactersIdNodeError
    )

  val idErrorsWithMessages: TableFor3[IdError, String, String] =
    Table(
      ("error", "message", "description"),
      (emptyIdScenarioError, "Scenario name is mandatory and cannot be empty", "Empty scenario name"),
      (emptyIdFragmentError, "Fragment name is mandatory and cannot be empty", "Empty fragment name"),
      (emptyIdNodeError, "Node name is mandatory and cannot be empty", "Empty node name"),
      (blankIdScenarioError, "Scenario name cannot be blank", "Blank scenario name"),
      (blankIdFragmentError, "Fragment name cannot be blank", "Blank fragment name"),
      (blankIdNodeError, "Node name cannot be blank", "Blank node name"),
      (leadingSpacesIdScenarioError, "Scenario name cannot have leading spaces", "Leading spaces in scenario name"),
      (leadingSpacesIdFragmentError, "Fragment name cannot have leading spaces", "Leading spaces in fragment name"),
      (leadingSpacesIdNodeError, "Node name cannot have leading spaces", "Leading spaces in node name"),
      (trailingSpacesIdScenarioError, "Scenario name cannot have trailing spaces", "Trailing spaces in scenario name"),
      (trailingSpacesIdFragmentError, "Fragment name cannot have trailing spaces", "Trailing spaces in fragment name"),
      (trailingSpacesIdNodeError, "Node name cannot have trailing spaces", "Trailing spaces in node name"),
      (
        illegalCharactersIdScenarioError,
        s"Scenario name contains invalid characters. $illegalCharsReadable are not allowed",
        "Invalid characters in scenario name"
      ),
      (
        illegalCharactersIdNodeError,
        s"Node name contains invalid characters. $illegalCharsReadable are not allowed",
        "Invalid characters in node name"
      )
    )

}
