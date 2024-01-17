package pl.touk.nussknacker.ui.component

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentId, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, FragmentOutput, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder.ToStaticDefinitionConverter
import pl.touk.nussknacker.restmodel.definition.UINodeEdges
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestProcessingTypes}

class EdgeTypesPreparerTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("return edge types for fragment, filters, switches and components with multiple inputs") {
    val sampleFragmentDef = new FragmentWithoutValidatorsDefinitionExtractor(getClass.getClassLoader)
      .extractFragmentComponentDefinition(
        ProcessTestData.sampleFragment,
        ComponentId.default(TestProcessingTypes.Streaming, _)
      )
      .validValue
    val definitionsWithFragments = ProcessTestData
      .modelDefinition()
      .toStaticComponentsDefinition
      .withComponent(
        ProcessTestData.sampleFragment.name.value,
        sampleFragmentDef
      )

    val edgeTypes = EdgeTypesPreparer.prepareEdgeTypes(definitionsWithFragments)

    edgeTypes.toSet shouldBe Set(
      UINodeEdges(
        BuiltInComponentInfo.Split,
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentInfo.Choice,
        List(NextSwitch(Expression.spel("true")), SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentInfo.Filter,
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      UINodeEdges(
        ComponentInfo(ComponentType.Fragment, ProcessTestData.sampleFragmentName.value),
        List(FragmentOutput("out1"), FragmentOutput("out2")),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      UINodeEdges(
        ComponentInfo(ComponentType.CustomComponent, "union"),
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = true
      )
    )
  }

}
