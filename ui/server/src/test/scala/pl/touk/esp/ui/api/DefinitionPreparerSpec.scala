package pl.touk.esp.ui.api

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.ui.api.DefinitionPreparer.{NodeEdges, NodeTypeId}
import pl.touk.esp.ui.api.helpers.TestFactory
import pl.touk.esp.ui.process.displayedgraph.displayablenode.EdgeType._
import pl.touk.esp.ui.process.values.{ParamDefaultValueConfig, TypeAfterConfig}
import pl.touk.esp.ui.security.{LoggedUser, Permission}

class DefinitionPreparerSpec extends FlatSpec with Matchers {

  it should "return objects sorted by label" in {

    val groups = DefinitionPreparer.prepareNodesToAdd(
      user = LoggedUser("aa", "", List(Permission.Admin), List()),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessRepo = TestFactory.sampleSubprocessRepository,
      extractorFactory = new TypeAfterConfig(new ParamDefaultValueConfig(Map())))

    groups.foreach { group =>
      group.possibleNodes.sortBy(_.label) shouldBe group.possibleNodes
    }
  }

  it should "return edge types for subprocess, filters and switches" in {

    val edgeTypes = DefinitionPreparer.prepareEdgeTypes(
      user = LoggedUser("aa", "", List(Permission.Admin), List()),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessRepo = TestFactory.sampleSubprocessRepository)

    edgeTypes.toSet shouldBe Set(
      NodeEdges(NodeTypeId("Split"), List(), true),
      NodeEdges(NodeTypeId("Switch"), List(NextSwitch(Expression("spel", "true")), SwitchDefault), true),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), false),
      NodeEdges(NodeTypeId("SubprocessInput", Some("sub1")), List(SubprocessOutput("out1"), SubprocessOutput("out2")), false)
    )

  }

}