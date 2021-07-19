package pl.touk.nussknacker.ui.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.restmodel.definition.{NodeEdges, NodeGroup, NodeTypeId}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType._
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestPermissions}
import pl.touk.nussknacker.ui.definition.defaults.{DefaultValueDeterminerChain, ParamDefaultValueConfig}
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

class DefinitionPreparerSpec extends FunSuite with Matchers with TestPermissions {

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)

  test("return groups sorted in order: inputs, base, other, outputs and then sorted by name within group") {

    val groups = prepareGroups(Map(), Map("custom" -> Some("CUSTOM"), "sinks"->Some("BAR")))

    groups.map(_.name) shouldBe List("sources", "base", "CUSTOM", "enrichers", "BAR", "optionalEndingCustom", "services")
  }

  test("return groups with hidden base group") {

    val groups = prepareGroups(Map.empty, Map("base" -> None))

    groups.map(_.name) shouldBe List("sources", "custom", "enrichers", "optionalEndingCustom", "services", "sinks")
  }

  test("return objects sorted by label case insensitive") {

    val groups = prepareGroupsOfNodes(List("foo","alaMaKota","BarFilter"))
    groups.map(_.possibleNodes.map(n=>n.label)) shouldBe List(
      List("filter", "mapVariable","split","sqlVariable","switch","variable"),
      List("alaMaKota","BarFilter","foo")
    )
  }

  test("return edge types for fragment, filters and switches") {
    val subprocessesDetails = TestFactory.sampleSubprocessRepository.loadSubprocesses(Map.empty)
    val edgeTypes = DefinitionPreparer.prepareEdgeTypes(
      user = TestFactory.adminUser("aa"),
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessesDetails = subprocessesDetails)

    edgeTypes.toSet shouldBe Set(
      NodeEdges(NodeTypeId("Split"), List(), true, false),
      NodeEdges(NodeTypeId("Switch"), List(NextSwitch(Expression("spel", "true")), SwitchDefault), true, false),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), false, false),
      NodeEdges(NodeTypeId("SubprocessInput", Some("sub1")), List(SubprocessOutput("out1"), SubprocessOutput("out2")), false, false)
    )

  }

  test("return objects sorted by label with mapped categories") {
    val groups = prepareGroups(Map(), Map("custom" -> Some("base"), "optionalEndingCustom" -> Some("base")))

    validateGroups(groups, 5)

    groups.exists(_.name == "custom") shouldBe false

    val baseNodeGroups = groups.filter(_.name == "base")
    baseNodeGroups should have size 1

    val baseNodes = baseNodeGroups.flatMap(_.possibleNodes)
    // 6 nodes from base + 3 custom nodes + 1 optional ending custom node
    baseNodes should have size (6 + 3 + 1)
    baseNodes.filter(n => n.`type` == "filter") should have size 1
    baseNodes.filter(n => n.`type` == "customNode") should have size 4

  }

  test("return objects sorted by label with mapped categories and mapped nodes") {

    val groups = prepareGroups(
      Map("barService" -> "foo", "barSource" -> "fooBar"),
      Map("custom" -> Some("base"), "optionalEndingCustom" -> Some("base")))

    validateGroups(groups, 7)

    groups.exists(_.name == "custom") shouldBe false

    val baseNodeGroups = groups.filter(_.name == "base")
    baseNodeGroups should have size 1

    val baseNodes = baseNodeGroups.flatMap(_.possibleNodes)
    // 6 nodes from base + 3 custom nodes + 1 optional ending custom node
    baseNodes should have size (6 + 3 + 1)
    baseNodes.filter(n => n.`type` == "filter") should have size 1
    baseNodes.filter(n => n.`type` == "customNode") should have size 4

    val fooNodes = groups.filter(_.name == "foo").flatMap(_.possibleNodes)
    fooNodes should have size 1
    fooNodes.filter(_.label == "barService") should have size 1

  }

  test("return custom nodes with correct group") {
    val initialDefinition = ProcessTestData.processDefinition
    val definitionWithCustomNodesInSomeCategory = initialDefinition.copy(
      customStreamTransformers = initialDefinition.customStreamTransformers.map {
        case (name, (objectDef, additionalData)) =>
          (name, (objectDef.copy(nodeConfig = objectDef.nodeConfig.copy(category = Some("cat1"))), additionalData))
      }
    )
    val groups = prepareGroups(Map.empty, Map.empty, definitionWithCustomNodesInSomeCategory)

    groups.exists(_.name == "custom") shouldBe false
    groups.exists(_.name == "cat1") shouldBe true
  }

  private def validateGroups(groups: List[NodeGroup], expectedSizeOfNotEmptyGroups: Int): Unit = {
    groups.filterNot(ng => ng.possibleNodes.isEmpty) should have size expectedSizeOfNotEmptyGroups
  }

  private def prepareGroups(fixedNodesConfig: Map[String, String], nodeCategoryMapping: Map[String, Option[String]],
                            processDefinition: ProcessDefinition[ObjectDefinition] = ProcessTestData.processDefinition): List[NodeGroup] = {
    // TODO: this is a copy paste from UIProcessObjectsFactory.prepareUIProcessObjects - should be refactored somehow
    val subprocessInputs = Map[String, ObjectDefinition]()
    val uiProcessDefinition = UIProcessObjectsFactory.createUIProcessDefinition(processDefinition, subprocessInputs, Set.empty)
    val dynamicNodesConfig = uiProcessDefinition.allDefinitions.mapValues(_.nodeConfig)
    val nodesConfig = NodesConfigCombiner.combine(fixedNodesConfig.mapValues(v => SingleNodeConfig(None, None, None, Some(v))), dynamicNodesConfig)

    val groups = DefinitionPreparer.prepareNodesToAdd(
      user = TestFactory.adminUser("aa"),
      processDefinition = uiProcessDefinition,
      isSubprocess = false,
      defaultsStrategy = DefaultValueDeterminerChain(ParamDefaultValueConfig(Map())),
      nodesConfig = nodesConfig,
      nodeCategoryMapping = nodeCategoryMapping,
      processCategoryService = processCategoryService,
      sinkAdditionalData = processDefinition.sinkFactories.mapValues(_._2),
      customTransformerAdditionalData = processDefinition.customStreamTransformers.mapValues(_._2)
    )
    groups
  }


  private def prepareGroupsOfNodes(services: List[String]): List[NodeGroup] = {

    val processDefinition = services.foldRight(ProcessDefinitionBuilder.empty)((s, p) => p.withService(s))
    val groups = DefinitionPreparer.prepareNodesToAdd(
      user = TestFactory.adminUser("aa"),
      processDefinition = UIProcessObjectsFactory.createUIProcessDefinition(processDefinition, Map(), Set.empty),
      isSubprocess = false,
      defaultsStrategy = DefaultValueDeterminerChain(ParamDefaultValueConfig(Map())),
      nodesConfig = Map(),
      nodeCategoryMapping =  Map(),
      processCategoryService = processCategoryService,
      sinkAdditionalData = processDefinition.sinkFactories.mapValues(_._2),
      customTransformerAdditionalData = processDefinition.customStreamTransformers.mapValues(_._2)
    )
    groups
  }
}
