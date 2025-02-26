package pl.touk.nussknacker.ui.definition.component

import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.definition.component.bultin.BuiltInComponentsDefinitionsPreparer
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  Components,
  CustomComponentSpecificData
}
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.node.WithParameters
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.restmodel.definition.UIComponentGroup
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.definition.{AlignedComponentsDefinitionProvider, component}

class ComponentGroupsPreparerSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with ValidatedValuesDetailedMessage {

  test("return groups sorted in order: inputs, base, other, outputs and then sorted by name within group") {
    val groups = ComponentGroupsPreparer.prepareComponentGroups(
      prepareComponentsDefinitionForTestData(
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
      prepareComponentsDefinitionForTestData(Map(ComponentGroupName("base") -> None))
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
      prepareComponentsDefinitionForTestData(
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
      .filter(n => n.componentId.`type` == ComponentType.BuiltIn)
      .map(_.componentId) should contain allElementsOf BuiltInComponentId.AllAvailableForScenario
    baseComponents.filter(n => n.componentId.`type` == ComponentType.CustomComponent) should have size 5
  }

  test("return custom component with correct group") {
    val definitionWithCustomComponentInSomeGroup = withStaticDefinition(
      ModelDefinitionBuilder.empty
        .withCustom(
          name = "fooTransformer",
          returnType = Some(Unknown),
          componentSpecificData = CustomComponentSpecificData(canHaveManyInputs = false, canBeEnding = false),
          componentGroupName = Some(ComponentGroupName("group1")),
          designerWideComponentId = None
        )
        .build
        .components
    )
    val groups = ComponentGroupsPreparer.prepareComponentGroups(definitionWithCustomComponentInSomeGroup)

    groups.exists(_.name == ComponentGroupName("custom")) shouldBe false
    groups.exists(_.name == ComponentGroupName("group1")) shouldBe true
  }

  test("return default value defined in parameter") {
    val defaultValueExpression = Expression(Language.Spel, "'fooDefault'")
    val parameter = Parameter[String](ParameterName("fooParameter")).copy(defaultValue = Some(defaultValueExpression))
    val definition = withStaticDefinition(
      ModelDefinitionBuilder.empty
        .withCustom(
          "fooTransformer",
          Some(Unknown),
          CustomComponentSpecificData(canHaveManyInputs = false, canBeEnding = true),
          parameter
        )
        .build
        .components
    )

    val groups           = ComponentGroupsPreparer.prepareComponentGroups(definition)
    val transformerGroup = groups.find(_.name == ComponentGroupName("optionalEndingCustom")).value
    inside(transformerGroup.components.head.node) { case withParameters: WithParameters =>
      withParameters.parameters.head.expression shouldEqual defaultValueExpression
    }
  }

  test("return components for fragments") {
    val model =
      getAlignedComponentsWithStaticDefinition(ModelDefinitionBuilder.empty.build, Map.empty, forFragment = true)
    val groups = ComponentGroupsPreparer.prepareComponentGroups(model)
    groups.map(_.name) shouldEqual List(
      DefaultsComponentGroupName.FragmentsDefinitionGroupName,
      DefaultsComponentGroupName.BaseGroupName
    )
    val fragmentDefinitionComponentLabels =
      groups.find(_.name == DefaultsComponentGroupName.FragmentsDefinitionGroupName).value.components.map(_.label)
    fragmentDefinitionComponentLabels shouldEqual List(
      BuiltInComponentId.FragmentInputDefinition.name,
      BuiltInComponentId.FragmentOutputDefinition.name
    )
  }

  test("hide sources for fragments") {
    val model =
      getAlignedComponentsWithStaticDefinition(
        ModelDefinitionBuilder.empty.withUnboundedStreamSource("source").build,
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
    val modelDefinition = getAlignedComponentsWithStaticDefinition(
      services
        .foldRight(ModelDefinitionBuilder.empty)((s, p) => p.withService(s))
        .build,
      Map.empty
    )
    ComponentGroupsPreparer.prepareComponentGroups(modelDefinition)
  }

  private def prepareComponentsDefinitionForTestData(
      groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]
  ) = {
    val modelDefinition = ProcessTestData.modelDefinition(groupNameMapping)
    getAlignedComponentsWithStaticDefinition(modelDefinition, groupNameMapping)
  }

  private def getAlignedComponentsWithStaticDefinition(
      modelDefinition: ModelDefinition,
      groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]],
      forFragment: Boolean = false
  ) = {
    val alignedComponentsDefinitionProvider = new AlignedComponentsDefinitionProvider(
      new BuiltInComponentsDefinitionsPreparer(new ComponentsUiConfig(Map.empty, groupNameMapping)),
      new FragmentComponentDefinitionExtractor(
        getClass.getClassLoader,
        ClassDefinitionSet(Set.empty[ClassDefinition]),
        Some(_),
        DesignerWideComponentId.default("Streaming", _)
      ),
      modelDefinition,
      ProcessingMode.UnboundedStream
    )

    withStaticDefinition(
      alignedComponentsDefinitionProvider
        .getAlignedComponentsWithBuiltInComponentsAndFragments(
          forFragment,
          fragments = List.empty
        )
    )

  }

  private def withStaticDefinition(
      components: Components
  ): List[ComponentWithStaticDefinition] = {
    components.components
      .map {
        case methodBased: MethodBasedComponentDefinitionWithImplementation =>
          component.ComponentWithStaticDefinition(methodBased, methodBased.staticDefinition)
        case other => throw new IllegalStateException(s"Unexpected component class: ${other.getClass.getName}")
      }
  }

}
