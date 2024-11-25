package pl.touk.nussknacker.ui.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.{
  BuiltInComponentId,
  ComponentId,
  ComponentType,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, FragmentOutput, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.definition.UINodeEdges
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ProcessTestData

class EdgeTypesPreparerTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("return edge types for fragment, filters, switches and components with multiple inputs") {
    val sampleFragmentDef = new FragmentComponentDefinitionExtractor(
      getClass.getClassLoader,
      Set.empty,
      Some(_),
      DesignerWideComponentId.default(Streaming.stringify, _)
    )
      .extractFragmentComponentDefinition(ProcessTestData.sampleFragment, AllowedProcessingModes.All)
      .validValue
    val definitionsWithFragments = ProcessTestData
      .modelDefinition()
      .withComponent(sampleFragmentDef)

    val edgeTypes = EdgeTypesPreparer.prepareEdgeTypes(definitionsWithFragments.components.components)

    edgeTypes.toSet shouldBe Set(
      UINodeEdges(
        BuiltInComponentId.Split,
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentId.Choice,
        List(NextSwitch(Expression.spel("true")), SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentId.Filter,
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      UINodeEdges(
        ComponentId(ComponentType.Fragment, ProcessTestData.sampleFragmentName.value),
        List(FragmentOutput("out1"), FragmentOutput("out2")),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      UINodeEdges(
        ComponentId(ComponentType.CustomComponent, "union"),
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = true
      )
    )
  }

}
