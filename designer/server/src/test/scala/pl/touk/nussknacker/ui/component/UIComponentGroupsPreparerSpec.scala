package pl.touk.nussknacker.ui.component

import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsStaticDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  CustomComponentSpecificData
}
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.WithParameters
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.ToStaticDefinitionConverter
import pl.touk.nussknacker.restmodel.definition.UIComponentGroup
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.definition.{AdditionalUIConfigFinalizer, ModelDefinitionEnricher}

class UIComponentGroupsPreparerSpec
    extends AnyFunSuite
    with Matchers
    with TestPermissions
    with OptionValues
    with ValidatedValuesDetailedMessage {

  test("return groups sorted in order: inputs, base, other, outputs and then sorted by name within group") {
    val groups = ComponentGroupsPreparer.prepareComponentGroups(
      prepareModelDefinitionForTestData(
        Map(
          ComponentGroupName("custom") -> Some(ComponentGroupName("CUSTOM")),
          ComponentGroupName("sinks")  -> Some(ComponentGroupName("BAR"))
        )
      )
    )
    groups
      .map(_.name) shouldBe List("sources", "base", "CUSTOM", "enrichers", "BAR", "optionalEndingCustom", "services")
      .map(ComponentGroupName(_))
  }

  test("return groups with hidden base group") {
    val groups = ComponentGroupsPreparer.prepareComponentGroups(
      prepareModelDefinitionForTestData(Map(ComponentGroupName("base") -> None))
    )
    groups.map(_.name) shouldBe List("sources", "custom", "enrichers", "optionalEndingCustom", "services", "sinks").map(
      ComponentGroupName(_)
    )
  }

  test("return components sorted by label case insensitive") {
    val groups = prepareGroupForServices(List("foo", "alaMaKota", "BarFilter"))
    groups.map(_.components.map(n => n.label)) shouldBe List(
      List("choice", "filter", "record-variable", "split", "variable"),
      List("alaMaKota", "BarFilter", "foo")
    )
  }

  test("return components with mapped groups") {
    val groups = ComponentGroupsPreparer.prepareComponentGroups(
      prepareModelDefinitionForTestData(
        Map(
          ComponentGroupName("custom")               -> Some(ComponentGroupName("base")),
          ComponentGroupName("optionalEndingCustom") -> Some(ComponentGroupName("base"))
        )
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

  test("return custom component with correct group") {
    val definitionWithCustomComponentInSomeGroup =
      prepareModelDefinitionForTestData(Map.empty).transform {
        case component if component.componentType == ComponentType.CustomComponent =>
          val updatedComponentConfig =
            component.componentConfig.copy(componentGroup = Some(ComponentGroupName("group1")))
          component.copy(componentConfig = updatedComponentConfig)
        case other => other
      }
    val groups = ComponentGroupsPreparer.prepareComponentGroups(definitionWithCustomComponentInSomeGroup)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false
    groups.exists(_.name == ComponentGroupName("group1")) shouldBe true
  }

  test("return default value defined in parameter") {
    val defaultValueExpression = Expression("fooLang", "'fooDefault'")
    val parameter              = Parameter[String]("fooParameter").copy(defaultValue = Some(defaultValueExpression))
    val definition = ModelDefinitionBuilder.empty
      .withCustom(
        "fooTransformer",
        Some(Unknown),
        CustomComponentSpecificData(manyInputs = false, canBeEnding = true),
        parameter
      )
      .build
      .toStaticComponentsDefinition

    val groups           = ComponentGroupsPreparer.prepareComponentGroups(definition)
    val transformerGroup = groups.find(_.name == ComponentGroupName("optionalEndingCustom")).value
    inside(transformerGroup.components.head.node) { case withParameters: WithParameters =>
      withParameters.parameters.head.expression shouldEqual defaultValueExpression
    }
  }

  test("return components for fragments") {
    val model =
      enrichModelDefinitionWithBuiltInComponents(ModelDefinitionBuilder.empty.build, Map.empty, forFragment = true)
    val groups = ComponentGroupsPreparer.prepareComponentGroups(model)
    groups.map(_.name) shouldEqual List(
      DefaultsComponentGroupName.FragmentsDefinitionGroupName,
      DefaultsComponentGroupName.BaseGroupName
    )
    val fragmentDefinitionComponentLabels =
      groups.find(_.name == DefaultsComponentGroupName.FragmentsDefinitionGroupName).value.components.map(_.label)
    fragmentDefinitionComponentLabels shouldEqual List(
      BuiltInComponentInfo.FragmentInputDefinition.name,
      BuiltInComponentInfo.FragmentOutputDefinition.name
    )
  }

  test("hide sources for fragments") {
    val model =
      enrichModelDefinitionWithBuiltInComponents(
        ModelDefinitionBuilder.empty.withSource("source").build,
        Map.empty,
        forFragment = true
      )
    val groups = ComponentGroupsPreparer.prepareComponentGroups(model)
    groups.map(_.name) shouldEqual List(
      DefaultsComponentGroupName.FragmentsDefinitionGroupName,
      DefaultsComponentGroupName.BaseGroupName
    )
  }

  private def validateGroups(groups: List[UIComponentGroup], expectedSizeOfNotEmptyGroups: Int): Unit = {
    groups.filterNot(ng => ng.components.isEmpty) should have size expectedSizeOfNotEmptyGroups
  }

  private def prepareGroupForServices(services: List[String]): List[UIComponentGroup] = {
    val modelDefinition = enrichModelDefinitionWithBuiltInComponents(
      services
        .foldRight(ModelDefinitionBuilder.empty)((s, p) => p.withService(s))
        .build,
      Map.empty
    )
    ComponentGroupsPreparer.prepareComponentGroups(modelDefinition)
  }

  private def prepareModelDefinitionForTestData(
      groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]
  ) = {
    val modelDefinition = ProcessTestData.modelDefinition(groupNameMapping)
    enrichModelDefinitionWithBuiltInComponents(modelDefinition, groupNameMapping)
  }

  private def enrichModelDefinitionWithBuiltInComponents(
      modelDefinition: ModelDefinition[ComponentDefinitionWithImplementation],
      groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
      forFragment: Boolean = false
  ) = {
    val modelDefinitionEnricher = new ModelDefinitionEnricher(
      new BuiltInComponentsStaticDefinitionsPreparer(new ComponentsUiConfig(Map.empty, groupNameMapping)),
      new FragmentWithoutValidatorsDefinitionExtractor(getClass.getClassLoader),
      new AdditionalUIConfigFinalizer(AdditionalUIConfigProvider.empty),
      modelDefinition.toStaticComponentsDefinition
    )
    modelDefinitionEnricher
      .modelDefinitionWithBuiltInComponentsAndFragments(
        forFragment,
        fragmentScenarios = List.empty,
        TestProcessingTypes.Streaming
      )
  }

}
