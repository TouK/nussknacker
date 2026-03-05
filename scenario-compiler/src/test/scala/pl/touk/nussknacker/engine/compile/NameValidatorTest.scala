package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.{forAll, Table}
import org.scalatest.prop.TableFor2
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, NodeName, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.node.{Sink, Source}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef

class IdValidatorTest extends AnyFunSuite with Matchers {

  test("should handle all cases of scenario id validation") {
    forAll(IdValidationTestData.scenarioIdErrorCases) {
      (scenarioId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          NameValidator.validate(validScenario(scenarioId), isFragment = false) match {
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
          NameValidator.validate(validFragment(scenarioId), isFragment = true) match {
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
        NameValidator.validate(validScenario(nodeId = nodeId, nodeName = "validNodeName"), isFragment = true) match {
          case Validated.Invalid(errors) =>
            errors.toList shouldBe expectedErrors
          case Validated.Valid(_) =>
            expectedErrors shouldBe empty
        }
      }
    }
  }

  test("should handle all cases of node name validation") {
    forAll(IdValidationTestData.nodeNameErrorCases) {
      (nodeName: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          NameValidator.validate(validScenario(nodeId = "validNodeId", nodeName = nodeName), isFragment = true) match {
            case Validated.Invalid(errors) =>
              errors.toList shouldBe expectedErrors
            case Validated.Valid(_) =>
              expectedErrors shouldBe empty
          }
        }
    }
  }

  test("should validate scenario, node id and node name") {
    val scenarioWithEmptyIds = validScenario("", "", "")
    NameValidator.validate(scenarioWithEmptyIds, isFragment = false) match {
      case Validated.Invalid(errors) =>
        errors.toList should contain theSameElementsAs List(
          ScenarioNameError(EmptyValue, ProcessName(""), isFragment = false),
          NodeIdValidationError(EmptyValue, NodeId("")),
          NodeNameValidationError(EmptyValue, NodeId(""), NodeName(""))
        )
      case Validated.Valid(_) =>
        fail("Validation succeeded, but was expected to fail")
    }
  }

  test("should validate duplicated node names") {
    val scenarioWithDuplicatedNodeNames = CanonicalProcess(
      MetaData("id", StreamMetaData()),
      List(
        FlatNode(Source(NodeId("source1"), NodeName("same"), SourceRef("source", Nil))),
        FlatNode(Sink(NodeId("sink1"), NodeName("same"), SinkRef("sink", Nil))),
      )
    )

    NameValidator.validate(scenarioWithDuplicatedNodeNames, isFragment = false) match {
      case Validated.Invalid(errors) =>
        errors.toList should contain(
          DuplicatedNodeNames(Set(NodeName("same")), Set(NodeId("source1"), NodeId("sink1")))
        )
      case Validated.Valid(_) =>
        fail("Validation succeeded, but was expected to fail")
    }
  }

  private def validScenario(
      id: String = "id",
      nodeId: String = "nodeId",
      nodeName: String = "nodeName"
  ): CanonicalProcess =
    ScenarioBuilder
      .streaming(id)
      .source(nodeId, "source")
      .emptySink("sinkId", "sink")
      .mapAllNodes(_.map {
        case FlatNode(source: Source) =>
          FlatNode(source.copy(name = NodeName(nodeName)))
        case node =>
          node
      })

  private def validFragment(id: String): CanonicalProcess =
    ScenarioBuilder.fragmentWithInputNodeId(id, "input").emptySink("sinkId", "test")

}

object IdValidationTestData {

  val nodeIdErrorCases: TableFor2[String, List[IdError]] = Table(
    ("nodeId", "errors"),
    ("validId", List.empty),
    ("", List(NodeIdValidationError(EmptyValue, NodeId("")))),
    (" ", List(NodeIdValidationError(BlankId, NodeId(" ")))),
    (
      "trailingSpace ",
      List(NodeIdValidationError(TrailingSpacesId, NodeId("trailingSpace ")))
    ),
    (
      " leadingSpace",
      List(NodeIdValidationError(LeadingSpacesId, NodeId(" leadingSpace")))
    ),
    (
      " leadingAndTrailingSpace ",
      List(
        NodeIdValidationError(
          LeadingSpacesId,
          NodeId(" leadingAndTrailingSpace ")
        ),
        NodeIdValidationError(
          TrailingSpacesId,
          NodeId(" leadingAndTrailingSpace ")
        )
      )
    ),
  )

  val nodeNameErrorCases: TableFor2[String, List[IdError]] = Table(
    ("nodeName", "errors"),
    ("validName", List.empty),
    ("", List(NodeNameValidationError(EmptyValue, NodeId("validNodeId"), NodeName("")))),
    (" ", List(NodeNameValidationError(BlankId, NodeId("validNodeId"), NodeName(" ")))),
    (
      "trailingSpace ",
      List(NodeNameValidationError(TrailingSpacesId, NodeId("validNodeId"), NodeName("trailingSpace ")))
    ),
    (
      " leadingSpace",
      List(NodeNameValidationError(LeadingSpacesId, NodeId("validNodeId"), NodeName(" leadingSpace")))
    ),
    (
      " leadingAndTrailingSpace ",
      List(
        NodeNameValidationError(
          LeadingSpacesId,
          NodeId("validNodeId"),
          NodeName(" leadingAndTrailingSpace ")
        ),
        NodeNameValidationError(
          TrailingSpacesId,
          NodeId("validNodeId"),
          NodeName(" leadingAndTrailingSpace ")
        )
      )
    ),
    (
      "name.with.dot",
      List(
        NodeNameValidationError(
          IllegalCharactersId("Quotation mark (\"), dot (.) and apostrophe (')"),
          NodeId("validNodeId"),
          NodeName("name.with.dot")
        )
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
