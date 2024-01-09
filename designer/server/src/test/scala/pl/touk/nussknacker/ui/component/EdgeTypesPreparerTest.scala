package pl.touk.nussknacker.ui.component

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, FragmentOutput, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.ToStaticDefinitionConverter
import pl.touk.nussknacker.restmodel.definition.NodeEdges
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestCategories}

class EdgeTypesPreparerTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("return edge types for fragment, filters, switches and components with multiple inputs") {
    val sampleFragmentDef = new FragmentWithoutValidatorsDefinitionExtractor(getClass.getClassLoader)
      .extractFragmentComponentDefinition(ProcessTestData.sampleFragment)
      .validValue
      .toStaticDefinition(TestCategories.Category1)
    val definitionsWithFragments = ProcessTestData
      .modelDefinition()
      .toStaticComponentsDefinition
      .withComponent(
        ProcessTestData.sampleFragment.name.value,
        sampleFragmentDef
      )

    val edgeTypes = EdgeTypesPreparer.prepareEdgeTypes(definitionsWithFragments)

    edgeTypes.toSet shouldBe Set(
      NodeEdges(
        BuiltInComponentInfo.Split,
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      NodeEdges(
        BuiltInComponentInfo.Choice,
        List(NextSwitch(Expression.spel("true")), SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      NodeEdges(
        BuiltInComponentInfo.Filter,
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      NodeEdges(
        ComponentInfo(ComponentType.Fragment, "sub1"),
        List(FragmentOutput("out1"), FragmentOutput("out2")),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      NodeEdges(
        ComponentInfo(ComponentType.CustomComponent, "union"),
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = true
      )
    )
  }

}
