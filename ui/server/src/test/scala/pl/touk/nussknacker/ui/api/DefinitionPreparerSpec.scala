package pl.touk.nussknacker.ui.api

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.api.DefinitionPreparer.{NodeEdges, NodeTypeId}
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.EdgeType._
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{ParamDefaultValueConfig, TypeAfterConfig}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class DefinitionPreparerSpec extends FlatSpec with Matchers {

  it should "return objects sorted by label" in {

    val subprocessesDetails = TestFactory.sampleSubprocessRepository.loadSubprocesses(Map.empty)
    val groups = new DefinitionPreparer().prepareNodesToAdd(
      user = LoggedUser("aa", List(Permission.Admin), List()),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessesDetails = subprocessesDetails,
      extractorFactory = new TypeAfterConfig(new ParamDefaultValueConfig(Map()))
    )
    groups.map(_.name) shouldBe sorted

    groups should have size 7
    groups.foreach { group =>
      group.possibleNodes.map(_.label) shouldBe sorted
    }
  }

  it should "return edge types for subprocess, filters and switches" in {
    val subprocessesDetails = TestFactory.sampleSubprocessRepository.loadSubprocesses(Map.empty)
    val edgeTypes = new DefinitionPreparer().prepareEdgeTypes(
      user = LoggedUser("aa", List(Permission.Admin), List()),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessesDetails = subprocessesDetails)

    edgeTypes.toSet shouldBe Set(
      NodeEdges(NodeTypeId("Split"), List(), true),
      NodeEdges(NodeTypeId("Switch"), List(NextSwitch(Expression("spel", "true")), SwitchDefault), true),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), false),
      NodeEdges(NodeTypeId("SubprocessInput", Some("sub1")), List(SubprocessOutput("out1"), SubprocessOutput("out2")), false)
    )

  }

  it should "return objects sorted by label with mapped categories" in {

    val subprocessesDetails = TestFactory.sampleSubprocessRepository.loadSubprocesses(Map.empty)
    val groups = new DefinitionPreparer(Map("custom"->"base")).prepareNodesToAdd(
      user = LoggedUser("aa", List(Permission.Admin), List()),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessesDetails = subprocessesDetails,
      extractorFactory = new TypeAfterConfig(new ParamDefaultValueConfig(Map()))
    )
    groups.map(_.name) shouldBe sorted

    groups should have size 6
    groups.exists(_.name == "custom") shouldBe false
    groups.foreach { group =>
      group.possibleNodes.map(_.label) shouldBe sorted
    }

    val baseNodeGroups = groups.filter(_.name == "base")
    baseNodeGroups should have size 1

    val baseNodes = baseNodeGroups.flatMap(_.possibleNodes)
    // 4 nodes from base + 3 custom nodes
    baseNodes should have size (4 + 3)
    baseNodes.filter(n => n.`type` == "filter") should have size 1
    baseNodes.filter(n => n.`type` == "customNode") should have size 3

  }
}