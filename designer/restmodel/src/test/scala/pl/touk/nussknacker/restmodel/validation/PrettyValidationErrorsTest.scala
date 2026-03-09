package pl.touk.nussknacker.restmodel.validation

import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.{forAll, Table}
import org.scalatest.prop.TableFor3
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  BlankId,
  EmptyValue,
  FatalUnknownError,
  IdError,
  IllegalCharactersId,
  IncompatibleParameterDefinitionModification,
  LeadingSpacesId,
  NodeIdValidationError,
  NodeNameValidationError,
  ScenarioNameError,
  TrailingSpacesId
}
import pl.touk.nussknacker.engine.api.definition.{SpelParameterEditor, SpelTemplateParameterEditor}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.IncompatibleParameterDefinitionErrorDetails
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.DictKeyWithLabel
import pl.touk.nussknacker.engine.graph.node
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
        case NodeNameValidationError(errorType, _, _) =>
          errorType match {
            case ProcessCompilationError.EmptyValue | ProcessCompilationError.IllegalCharactersId(_) =>
              formattedErrorType shouldBe NodeValidationErrorType.RenderNotAllowed
            case _ => formattedErrorType shouldBe NodeValidationErrorType.SaveAllowed
          }
        case NodeIdValidationError(errorType, _) =>
          errorType match {
            case ProcessCompilationError.EmptyValue | ProcessCompilationError.IllegalCharactersId(_) =>
              formattedErrorType shouldBe NodeValidationErrorType.RenderNotAllowed
            case _ => formattedErrorType shouldBe NodeValidationErrorType.SaveAllowed
          }
        case ScenarioNameError(_, _, _) => formattedErrorType shouldBe NodeValidationErrorType.SaveAllowed
      }
    }
  }

  test("should properly map id error field names") {
    PrettyValidationErrors.formatErrorMessage(IdErrorTestData.blankIdScenarioError).fieldName shouldBe
      Some(CanonicalProcess.NameFieldName)
    PrettyValidationErrors.formatErrorMessage(IdErrorTestData.blankIdNodeError).fieldName shouldBe
      Some(node.NameFieldName)
    PrettyValidationErrors.formatErrorMessage(IdErrorTestData.blankIdNodeIdError).fieldName shouldBe
      Some(node.IdFieldName)
  }

  test("should properly map duplicated node names error") {
    PrettyValidationErrors
      .formatErrorMessage(
        ProcessCompilationError.DuplicatedNodeNames(Set(NodeName("same")), Set(NodeId("1"), NodeId("2")))
      ) shouldBe
      ValidationResults.NodeValidationError(
        typ = "DuplicatedNodeNames",
        message = "Two nodes cannot have same name",
        description = "Duplicate node names: same",
        fieldName = None,
        errorType = NodeValidationErrorType.SaveNotAllowed,
        details = None
      )
  }

  test("should properly map duplicated node ids error with node names") {
    PrettyValidationErrors
      .formatErrorMessage(
        ProcessCompilationError.DuplicatedNodeIds(
          Map(NodeId("id1") -> Set(NodeName("source"), NodeName("sink")))
        )
      ) shouldBe
      ValidationResults.NodeValidationError(
        typ = "DuplicatedNodeIds",
        message = "Two nodes cannot have same id",
        description = "Duplicate node ids: id1 (sink, source)",
        fieldName = None,
        errorType = NodeValidationErrorType.RenderNotAllowed,
        details = None
      )
  }

  test("should use node name in incompatible parameter error message when provided") {
    val nodeId    = NodeId("node-id")
    val paramName = ParameterName("param1")
    val editors   = List(SpelParameterEditor, SpelTemplateParameterEditor)
    val error =
      IncompatibleParameterDefinitionModification(
        paramName,
        DictKeyWithLabel,
        editors,
        nodeId,
        NodeName("friendly-node")
      )

    val formatted = PrettyValidationErrors.formatErrorMessage(error)

    formatted.message should include("component [friendly-node]")
    formatted.message should not include "component [node-id]"
    formatted.details shouldBe Some(IncompatibleParameterDefinitionErrorDetails(paramName, editors, nodeId))
  }

  test("should prefer node name over node id for fatal unknown error") {
    val formatted = PrettyValidationErrors.formatErrorMessage(
      FatalUnknownError("boom", Some(NodeId("node-id")), Some(NodeName("friendly-node")))
    )

    formatted.message should include("for node friendly-node")
    formatted.message should not include "for node node-id"
  }

}

object IdErrorTestData {

  val emptyIdScenarioError: ScenarioNameError   = ScenarioNameError(EmptyValue, ProcessName(""), isFragment = false)
  val emptyIdFragmentError: ScenarioNameError   = ScenarioNameError(EmptyValue, ProcessName(""), isFragment = true)
  val emptyIdNodeError: NodeNameValidationError = NodeNameValidationError(EmptyValue, NodeId(""), NodeName(""))

  val blankIdScenarioError: ScenarioNameError   = ScenarioNameError(BlankId, ProcessName(" "), isFragment = false)
  val blankIdFragmentError: ScenarioNameError   = ScenarioNameError(BlankId, ProcessName(" "), isFragment = true)
  val blankIdNodeError: NodeNameValidationError = NodeNameValidationError(BlankId, NodeId(" "), NodeName(""))
  val blankIdNodeIdError: NodeIdValidationError = NodeIdValidationError(BlankId, NodeId(" "))

  val leadingSpacesIdScenarioError: ScenarioNameError =
    ScenarioNameError(LeadingSpacesId, ProcessName(" leadingSpace"), isFragment = false)
  val leadingSpacesIdFragmentError: ScenarioNameError =
    ScenarioNameError(LeadingSpacesId, ProcessName(" leadingSpace"), isFragment = true)
  val leadingSpacesIdNodeError: NodeNameValidationError =
    NodeNameValidationError(LeadingSpacesId, NodeId(""), NodeName(" leadingSpace"))
  val leadingSpacesIdNodeIdError: NodeIdValidationError =
    NodeIdValidationError(LeadingSpacesId, NodeId(" leadingSpace"))

  val trailingSpacesIdScenarioError: ScenarioNameError =
    ScenarioNameError(TrailingSpacesId, ProcessName("trailingSpace "), isFragment = false)
  val trailingSpacesIdFragmentError: ScenarioNameError =
    ScenarioNameError(TrailingSpacesId, ProcessName("trailingSpace "), isFragment = true)
  val trailingSpacesIdNodeError: NodeNameValidationError =
    NodeNameValidationError(TrailingSpacesId, NodeId(""), NodeName("trailingSpace "))
  val trailingSpacesIdNodeIdError: NodeIdValidationError =
    NodeIdValidationError(TrailingSpacesId, NodeId("trailingSpace "))

  val illegalCharsReadable = "Some illegal character (x), another illegal character (!)"
  val illegalCharactersIdScenarioError: ScenarioNameError =
    ScenarioNameError(IllegalCharactersId(illegalCharsReadable), ProcessName("idWithIllegalChars!"), isFragment = false)
  val illegalCharactersIdNodeError: NodeNameValidationError =
    NodeNameValidationError(IllegalCharactersId(illegalCharsReadable), NodeId(""), NodeName("idWithIllegalChars!"))
  val illegalCharactersIdNodeIdError: NodeIdValidationError =
    NodeIdValidationError(IllegalCharactersId(illegalCharsReadable), NodeId("idWithIllegalChars!"))

  val emptyIdNodeIdError: NodeIdValidationError = NodeIdValidationError(EmptyValue, NodeId(""))

  val allIdErrors: Set[IdError] =
    Set(
      emptyIdScenarioError,
      emptyIdFragmentError,
      emptyIdNodeError,
      emptyIdNodeIdError,
      blankIdScenarioError,
      blankIdFragmentError,
      blankIdNodeError,
      blankIdNodeIdError,
      leadingSpacesIdScenarioError,
      leadingSpacesIdFragmentError,
      leadingSpacesIdNodeError,
      leadingSpacesIdNodeIdError,
      trailingSpacesIdScenarioError,
      trailingSpacesIdFragmentError,
      trailingSpacesIdNodeError,
      trailingSpacesIdNodeIdError,
      illegalCharactersIdScenarioError,
      illegalCharactersIdNodeError,
      illegalCharactersIdNodeIdError
    )

  val idErrorsWithMessages: TableFor3[IdError, String, String] =
    Table(
      ("error", "message", "description"),
      (emptyIdScenarioError, "Scenario name is mandatory and cannot be empty", "Empty scenario name"),
      (emptyIdFragmentError, "Fragment name is mandatory and cannot be empty", "Empty fragment name"),
      (emptyIdNodeError, "Node name is mandatory and cannot be empty", "Empty node name"),
      (emptyIdNodeIdError, "Node id is mandatory and cannot be empty", "Empty node id"),
      (blankIdScenarioError, "Scenario name cannot be blank", "Blank scenario name"),
      (blankIdFragmentError, "Fragment name cannot be blank", "Blank fragment name"),
      (blankIdNodeError, "Node name cannot be blank", "Blank node name"),
      (blankIdNodeIdError, "Node id cannot be blank", "Blank node id"),
      (leadingSpacesIdScenarioError, "Scenario name cannot have leading spaces", "Leading spaces in scenario name"),
      (leadingSpacesIdFragmentError, "Fragment name cannot have leading spaces", "Leading spaces in fragment name"),
      (leadingSpacesIdNodeError, "Node name cannot have leading spaces", "Leading spaces in node name"),
      (leadingSpacesIdNodeIdError, "Node id cannot have leading spaces", "Leading spaces in node id"),
      (trailingSpacesIdScenarioError, "Scenario name cannot have trailing spaces", "Trailing spaces in scenario name"),
      (trailingSpacesIdFragmentError, "Fragment name cannot have trailing spaces", "Trailing spaces in fragment name"),
      (trailingSpacesIdNodeError, "Node name cannot have trailing spaces", "Trailing spaces in node name"),
      (trailingSpacesIdNodeIdError, "Node id cannot have trailing spaces", "Trailing spaces in node id"),
      (
        illegalCharactersIdScenarioError,
        s"Scenario name contains invalid characters. $illegalCharsReadable are not allowed",
        "Invalid characters in scenario name"
      ),
      (
        illegalCharactersIdNodeError,
        s"Node name contains invalid characters. $illegalCharsReadable are not allowed",
        "Invalid characters in node name"
      ),
      (
        illegalCharactersIdNodeIdError,
        s"Node id contains invalid characters. $illegalCharsReadable are not allowed",
        "Invalid characters in node id"
      )
    )

}
