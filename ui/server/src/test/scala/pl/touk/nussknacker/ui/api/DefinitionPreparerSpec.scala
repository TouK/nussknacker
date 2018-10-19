package pl.touk.nussknacker.ui.api

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.ui.api.DefinitionPreparer.{NodeEdges, NodeTypeId}
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestPermissions}
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.EdgeType._
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.nussknacker.ui.process.uiconfig.defaults.{DefaultValueExtractorChain, ParamDefaultValueConfig}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

class DefinitionPreparerSpec extends FlatSpec with Matchers with TestPermissions{

  it should "return groups sorted by name" in {

    val groups = prepareGroups(Map(), Map("custom" -> "CUSTOM", "sinks"->"BAR"))

    groups.map(_.name) shouldBe List("BAR","base", "CUSTOM", "enrichers", "sources")
  }

  it should "return objects sorted by label case insensitive" in {

    val groups = prepareGroupsOfNodes(List("foo","alaMaKota","BarFilter"))
    groups.map(_.possibleNodes.map(n=>n.label)) shouldBe List(
      List("filter","split","sqlVariable","switch","variable"),
      List("alaMaKota","BarFilter","foo")
    )
  }

  it should "return edge types for subprocess, filters and switches" in {
    val subprocessesDetails = TestFactory.sampleSubprocessRepository.loadSubprocesses(Map.empty)
    val edgeTypes = DefinitionPreparer.prepareEdgeTypes(
      user = LoggedUser("aa", testPermissionAdmin),
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
    val groups = prepareGroups(Map(), Map("custom" -> "base"))

    validateGroups(groups, 4)

    groups.exists(_.name == "custom") shouldBe false

    val baseNodeGroups = groups.filter(_.name == "base")
    baseNodeGroups should have size 1

    val baseNodes = baseNodeGroups.flatMap(_.possibleNodes)
    // 5 nodes from base + 3 custom nodes
    baseNodes should have size (5 + 3)
    baseNodes.filter(n => n.`type` == "filter") should have size 1
    baseNodes.filter(n => n.`type` == "customNode") should have size 3

  }

  it should "return objects sorted by label with mapped categories and mapped nodes" in {

    val groups = prepareGroups(
      Map("barService" -> "foo", "barSource" -> "fooBar"),
      Map("custom" -> "base"))

    validateGroups(groups, 5)

    groups.exists(_.name == "custom") shouldBe false

    val baseNodeGroups = groups.filter(_.name == "base")
    baseNodeGroups should have size 1

    val baseNodes = baseNodeGroups.flatMap(_.possibleNodes)
    // 5 nodes from base + 3 custom nodes
    baseNodes should have size (5 + 3)
    baseNodes.filter(n => n.`type` == "filter") should have size 1
    baseNodes.filter(n => n.`type` == "customNode") should have size 3

    val fooNodes = groups.filter(_.name == "foo").flatMap(_.possibleNodes)
    fooNodes should have size 1
    fooNodes.filter(_.label == "barService") should have size 1

  }

  private def validateGroups(groups: List[NodeGroup], expectedSizeOfNotEmptyGroups: Int): Unit = {
    groups.map(_.name) shouldBe sorted
    groups.filterNot(ng => ng.possibleNodes.isEmpty) should have size expectedSizeOfNotEmptyGroups
  }

  private def prepareGroups(nodesConfig: Map[String, String], nodeCategoryMapping: Map[String, String]): List[NodeGroup] = {
    val subprocessesDetails = TestFactory.sampleSubprocessRepository.loadSubprocesses(Map.empty)
    val subprocessInputs = Map[String, ObjectDefinition]()

    val groups = DefinitionPreparer.prepareNodesToAdd(
      user = LoggedUser("aa", testPermissionAdmin),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessInputs = subprocessInputs,
      extractorFactory = DefaultValueExtractorChain(ParamDefaultValueConfig(Map()), ModelClassLoader.empty),
      nodesConfig = nodesConfig.mapValues(v => SingleNodeConfig(None, None, None, Some(v))),
      nodeCategoryMapping = nodeCategoryMapping
    )
    groups
  }


  private def prepareGroupsOfNodes(services: List[String]): List[NodeGroup] = {

    val groups = DefinitionPreparer.prepareNodesToAdd(
      user = LoggedUser("aa", testPermissionAdmin),
      processDefinition = services.foldRight(ProcessDefinitionBuilder.empty)((s, p) => p.withService(s)),
      isSubprocess = false,
      subprocessInputs = Map(),
      extractorFactory = DefaultValueExtractorChain(ParamDefaultValueConfig(Map()), ModelClassLoader.empty),
      nodesConfig = Map(),
      nodeCategoryMapping =  Map()
    )
    groups
  }
}