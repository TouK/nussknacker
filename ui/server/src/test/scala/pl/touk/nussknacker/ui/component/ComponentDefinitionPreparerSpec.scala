package pl.touk.nussknacker.ui.component

import org.scalatest.Inside.inside
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.WithParameters
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.restmodel.definition.{ComponentGroup, NodeEdges, NodeTypeId}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType._
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestPermissions}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

class ComponentDefinitionPreparerSpec extends FunSuite with Matchers with TestPermissions with OptionValues {

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)

  test("return groups sorted in order: inputs, base, other, outputs and then sorted by name within group") {
    val groups = prepareGroups(Map(), Map(ComponentGroupName("custom") -> Some(ComponentGroupName("CUSTOM")), ComponentGroupName("sinks") -> Some(ComponentGroupName("BAR"))))
    groups.map(_.name) shouldBe List("sources", "base", "CUSTOM", "enrichers", "BAR", "optionalEndingCustom", "services").map(ComponentGroupName(_))
  }

  test("return groups with hidden base group") {
    val groups = prepareGroups(Map.empty, Map(ComponentGroupName("base") -> None))
    groups.map(_.name) shouldBe List("sources", "custom", "enrichers", "optionalEndingCustom", "services", "sinks").map(ComponentGroupName(_))
  }

  test("return objects sorted by label case insensitive") {
    val groups = prepareGroupsOfNodes(List("foo","alaMaKota","BarFilter"))
    groups.map(_.components.map(n=>n.label)) shouldBe List(
      List("filter", "mapVariable","split","switch","variable"),
      List("alaMaKota","BarFilter","foo")
    )
  }

  test("return edge types for fragment, filters and switches") {
    val subprocessesDetails = TestFactory.prepareSampleSubprocessRepository.loadSubprocesses(Map.empty)

    val edgeTypes = ComponentDefinitionPreparer.prepareEdgeTypes(
      processDefinition = ProcessTestData.processDefinition,
      isSubprocess = false,
      subprocessesDetails = subprocessesDetails
    )

    edgeTypes.toSet shouldBe Set(
      NodeEdges(NodeTypeId("Split"), List(), true, false),
      NodeEdges(NodeTypeId("Switch"), List(NextSwitch(Expression("spel", "true")), SwitchDefault), true, false),
      NodeEdges(NodeTypeId("Filter"), List(FilterTrue, FilterFalse), false, false),
      NodeEdges(NodeTypeId("SubprocessInput", Some("sub1")), List(SubprocessOutput("out1"), SubprocessOutput("out2")), false, false)
    )
  }

  test("return objects sorted by label with mapped categories") {
    val groups = prepareGroups(Map(), Map(ComponentGroupName("custom") -> Some(ComponentGroupName("base")), ComponentGroupName("optionalEndingCustom") -> Some(ComponentGroupName("base"))))

    validateGroups(groups, 5)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false

    val baseComponentsGroups = groups.filter(_.name == ComponentGroupName("base"))
    baseComponentsGroups should have size 1

    val baseComponents = baseComponentsGroups.flatMap(_.components)
    // 5 nodes from base + 3 custom nodes + 1 optional ending custom node
    baseComponents should have size (5 + 3 + 1)
    baseComponents.filter(n => n.`type` == "filter") should have size 1
    baseComponents.filter(n => n.`type` == "customNode") should have size 4

  }

  test("return objects sorted by label with mapped categories and mapped nodes") {

    val groups = prepareGroups(
      Map("barService" -> "foo", "barSource" -> "fooBar"),
      Map(ComponentGroupName("custom") -> Some(ComponentGroupName("base")), ComponentGroupName("optionalEndingCustom") -> Some(ComponentGroupName("base"))))

    validateGroups(groups, 7)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false

    val baseComponentsGroups = groups.filter(_.name == ComponentGroupName("base"))
    baseComponentsGroups should have size 1

    val baseComponents = baseComponentsGroups.flatMap(_.components)
    // 5 nodes from base + 3 custom nodes + 1 optional ending custom node
    baseComponents should have size (5 + 3 + 1)
    baseComponents.filter(n => n.`type` == "filter") should have size 1
    baseComponents.filter(n => n.`type` == "customNode") should have size 4

    val fooNodes = groups.filter(_.name == ComponentGroupName("foo")).flatMap(_.components)
    fooNodes should have size 1
    fooNodes.filter(_.label == "barService") should have size 1

  }

  test("return custom nodes with correct group") {
    val initialDefinition = ProcessTestData.processDefinition
    val definitionWithCustomNodesInSomeCategory = initialDefinition.copy(
      customStreamTransformers = initialDefinition.customStreamTransformers.map {
        case (name, (objectDef, additionalData)) =>
          (name, (objectDef.copy(componentConfig = objectDef.componentConfig.copy(componentGroup = Some(ComponentGroupName("cat1")))), additionalData))
      }
    )
    val groups = prepareGroups(Map.empty, Map.empty, definitionWithCustomNodesInSomeCategory)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false
    groups.exists(_.name == ComponentGroupName("cat1")) shouldBe true
  }

  test("return default value defined in parameter") {
    val defaultValue = "'fooDefault'"
    val parameter = Parameter[String]("fooParameter").copy(defaultValue = Some(defaultValue))
    val definition = ProcessDefinitionBuilder.empty.withCustomStreamTransformer("fooTransformer", classOf[Object],
      CustomTransformerAdditionalData(Set.empty, clearsContext = false, manyInputs = false, canBeEnding = true), parameter)

    val groups = prepareGroups(Map.empty, Map.empty, definition)
    val transformerGroup = groups.find(_.name == ComponentGroupName("optionalEndingCustom")).value
    inside(transformerGroup.components.head.node) {
      case withParameters: WithParameters =>
        withParameters.parameters.head.expression.expression shouldEqual defaultValue
    }
  }

  test("should prefer config over code configuration") {
    val fixed = Map(
      "service" -> SingleComponentConfig(None, None, Some("doc"), None),
      "serviceA" -> SingleComponentConfig(None, None, Some("doc"), None)
    )

    val dynamic = Map(
      "service" -> SingleComponentConfig(None, None, Some("doc1"), None),
      "serviceB" -> SingleComponentConfig(None, None, Some("doc"), None)
    )

    val expected = Map(
      "service" -> SingleComponentConfig(None, None, Some("doc"), None),
      "serviceA" -> SingleComponentConfig(None, None, Some("doc"), None),
      "serviceB" -> SingleComponentConfig(None, None, Some("doc"), None)
    )

    ComponentDefinitionPreparer.combineComponentsConfig(fixed, dynamic) shouldBe expected
  }

  test("should merge default value maps") {
    val fixed = Map(
      "service" -> SingleComponentConfig(Some(Map("a" -> "x", "b" -> "y").mapValues(dv => ParameterConfig(Some(dv), None, None, None))), None, Some("doc"), None)
    )

    val dynamic = Map(
      "service" -> SingleComponentConfig(Some(Map("a" -> "xx", "c" -> "z").mapValues(dv => ParameterConfig(Some(dv), None, None, None))), None, Some("doc1"), None)
    )

    val expected = Map(
      "service" -> SingleComponentConfig(
        Some(Map("a" -> "x", "b" -> "y", "c" -> "z").mapValues(dv => ParameterConfig(Some(dv), None, None, None))),
        None,
        Some("doc"),
        None
      )
    )

    ComponentDefinitionPreparer.combineComponentsConfig(fixed, dynamic) shouldBe expected
  }

  private def validateGroups(groups: List[ComponentGroup], expectedSizeOfNotEmptyGroups: Int): Unit = {
    groups.filterNot(ng => ng.components.isEmpty) should have size expectedSizeOfNotEmptyGroups
  }

  private def prepareGroups(fixedConfig: Map[String, String], componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
                            processDefinition: ProcessDefinition[ObjectDefinition] = ProcessTestData.processDefinition): List[ComponentGroup] = {
    // TODO: this is a copy paste from UIProcessObjectsFactory.prepareUIProcessObjects - should be refactored somehow
    val subprocessInputs = Map[String, ObjectDefinition]()
    val uiProcessDefinition = UIProcessObjectsFactory.createUIProcessDefinition(processDefinition, subprocessInputs, Set.empty)
    val dynamicComponentsConfig = uiProcessDefinition.allDefinitions.mapValues(_.componentConfig)
    val fixedComponentsConfig = fixedConfig.mapValues(v => SingleComponentConfig(None, None, None, Some(ComponentGroupName(v))))
    val componentsConfig = ComponentDefinitionPreparer.combineComponentsConfig(fixedComponentsConfig, dynamicComponentsConfig)

    val groups = ComponentDefinitionPreparer.prepareComponentsGroupList(
      user = TestFactory.adminUser("aa"),
      processDefinition = uiProcessDefinition,
      isSubprocess = false,
      componentsConfig = componentsConfig,
      componentsGroupMapping = componentsGroupMapping,
      processCategoryService = processCategoryService,
      customTransformerAdditionalData = processDefinition.customStreamTransformers.mapValues(_._2)
    )
    groups
  }

  private def prepareGroupsOfNodes(services: List[String]): List[ComponentGroup] = {
    val processDefinition = services.foldRight(ProcessDefinitionBuilder.empty)((s, p) => p.withService(s))
    val groups = ComponentDefinitionPreparer.prepareComponentsGroupList(
      user = TestFactory.adminUser("aa"),
      processDefinition = UIProcessObjectsFactory.createUIProcessDefinition(processDefinition, Map(), Set.empty),
      isSubprocess = false,
      componentsConfig = Map(),
      componentsGroupMapping =  Map(),
      processCategoryService = processCategoryService,
      customTransformerAdditionalData = processDefinition.customStreamTransformers.mapValues(_._2)
    )
    groups
  }
}
