package pl.touk.nussknacker.ui.component

import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, CustomComponentSpecificData}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.WithParameters
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.ComponentDefinitionBuilder
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.definition.ComponentGroup
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.definition.ModelDefinitionEnricher
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

class ComponentGroupsPreparerSpec
    extends AnyFunSuite
    with Matchers
    with TestPermissions
    with OptionValues
    with ValidatedValuesDetailedMessage {

  private val processCategoryService = TestFactory.createCategoryService(ConfigWithScalaVersion.TestsConfig)

  test("return groups sorted in order: inputs, base, other, outputs and then sorted by name within group") {
    val groups = prepareGroups(
      Map.empty,
      Map(
        ComponentGroupName("custom") -> Some(ComponentGroupName("CUSTOM")),
        ComponentGroupName("sinks")  -> Some(ComponentGroupName("BAR"))
      )
    )
    groups
      .map(_.name) shouldBe List("sources", "base", "CUSTOM", "enrichers", "BAR", "optionalEndingCustom", "services")
      .map(ComponentGroupName(_))
  }

  test("return groups with hidden base group") {
    val groups = prepareGroups(Map.empty, Map(ComponentGroupName("base") -> None))
    groups.map(_.name) shouldBe List("sources", "custom", "enrichers", "optionalEndingCustom", "services", "sinks").map(
      ComponentGroupName(_)
    )
  }

  test("return objects sorted by label case insensitive") {
    val groups = prepareGroupForServices(List("foo", "alaMaKota", "BarFilter"))
    groups.map(_.components.map(n => n.label)) shouldBe List(
      List("choice", "filter", "record-variable", "split", "variable"),
      List("alaMaKota", "BarFilter", "foo")
    )
  }

  test("return objects with mapped groups") {
    val groups = prepareGroups(
      Map(),
      Map(
        ComponentGroupName("custom")               -> Some(ComponentGroupName("base")),
        ComponentGroupName("optionalEndingCustom") -> Some(ComponentGroupName("base"))
      )
    )

    validateGroups(groups, 5)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false

    val baseComponentsGroups = groups.filter(_.name == ComponentGroupName("base"))
    baseComponentsGroups should have size 1

    val baseComponents = baseComponentsGroups.flatMap(_.components)
    baseComponents
      .filter(n => n.`type` == ComponentType.BuiltIn)
      .map(_.label) should contain allElementsOf BuiltInComponentInfo.AllAvailableForScenario.map(_.name)
    baseComponents.filter(n => n.`type` == ComponentType.CustomComponent) should have size 5
  }

  test("return objects with mapped nodes") {
    val groups = prepareGroups(
      Map("barService" -> ComponentGroupName("foo"), "barSource" -> ComponentGroupName("fooBar")),
      Map.empty
    )

    val fooNodes = groups.filter(_.name == ComponentGroupName("foo")).flatMap(_.components)
    fooNodes should have size 1
    fooNodes.filter(_.label == "barService") should have size 1
  }

  test("return custom nodes with correct group") {
    val definitionWithCustomNodesInSomeCategory = ProcessTestData.modelDefinition.transform {
      case component if component.componentType == ComponentType.CustomComponent =>
        val updatedComponentConfig = component.componentConfig.copy(componentGroup = Some(ComponentGroupName("cat1")))
        component.copy(componentConfig = updatedComponentConfig)
      case other => other
    }
    val groups = prepareGroups(Map.empty, Map.empty, definitionWithCustomNodesInSomeCategory)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false
    groups.exists(_.name == ComponentGroupName("cat1")) shouldBe true
  }

  test("return default value defined in parameter") {
    val defaultValueExpression = Expression("fooLang", "'fooDefault'")
    val parameter              = Parameter[String]("fooParameter").copy(defaultValue = Some(defaultValueExpression))
    val definition = ModelDefinitionBuilder.empty
      .withCustomStreamTransformer(
        "fooTransformer",
        Some(Unknown),
        CustomComponentSpecificData(manyInputs = false, canBeEnding = true),
        parameter
      )

    val groups           = prepareGroups(Map.empty, Map.empty, definition)
    val transformerGroup = groups.find(_.name == ComponentGroupName("optionalEndingCustom")).value
    inside(transformerGroup.components.head.node) { case withParameters: WithParameters =>
      withParameters.parameters.head.expression shouldEqual defaultValueExpression
    }
  }

  private def validateGroups(groups: List[ComponentGroup], expectedSizeOfNotEmptyGroups: Int): Unit = {
    groups.filterNot(ng => ng.components.isEmpty) should have size expectedSizeOfNotEmptyGroups
  }

  private def prepareGroupForServices(services: List[String]): List[ComponentGroup] = {
    val modelDefinition = services
      .foldRight(ModelDefinitionBuilder.empty)((s, p) => p.withService(s))
    prepareGroups(Map.empty, Map.empty, modelDefinition)
  }

  private def prepareGroups(
      groupOverrides: Map[String, ComponentGroupName],
      componentsGroupMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
      modelDefinition: ModelDefinition[ComponentStaticDefinition] = ProcessTestData.modelDefinition
  ): List[ComponentGroup] = {
    val enrichedModelDefinition = new ModelDefinitionEnricher(ComponentsUiConfig.Empty, getClass.getClassLoader)
      .enrichModelDefinitionWithBuiltInComponentsAndFragments(
        modelDefinition,
        forFragment = false,
        fragmentsDetails = List.empty
      )
    val fixedComponentsConfig = ComponentsUiConfig(
      groupOverrides.mapValuesNow(v => SingleComponentConfig(None, None, None, Some(v), None))
    )
    val groups = new ComponentGroupsPreparer(componentsGroupMapping).prepareComponentGroups(
      user = TestFactory.adminUser("aa"),
      definitions = enrichedModelDefinition,
      componentsConfig = fixedComponentsConfig,
      processCategoryService = processCategoryService,
      TestProcessingTypes.Streaming
    )
    groups
  }

}
