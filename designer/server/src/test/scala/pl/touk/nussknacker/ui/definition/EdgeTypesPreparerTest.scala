package pl.touk.nussknacker.ui.definition

import cats.data.NonEmptyList
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.component.{
  BuiltInComponentId,
  ComponentId,
  ComponentOutput,
  ComponentType,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.definition.component.CustomComponentSpecificData
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, FragmentOutput, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.restmodel.definition.UINodeEdges
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ProcessTestData

class EdgeTypesPreparerTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("return edge types for fragment, filters, switches and components with multiple inputs") {
    val sampleFragmentDef = new FragmentComponentDefinitionExtractor(
      getClass.getClassLoader,
      ClassDefinitionSet(Set.empty[ClassDefinition]),
      Some(_),
      DesignerWideComponentId.default(Streaming.stringify, _),
      GlobalParametersConfig.default
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

  test("return an additional output entry for a component declaring them, join keeps its input entry only") {
    val componentWithOutputs = "componentWithOutputs"
    val namedMainComponent   = "namedMainComponent"
    val joinComponent        = "joinComponent"
    // A plain custom component gets no entry at all - asserted by its absence from the expected set below.
    val plainComponent = "plainComponent"

    val definitions = ModelDefinitionBuilder
      .empty(Map.empty)
      .withCustom(
        componentWithOutputs,
        Some(Unknown),
        CustomComponentSpecificData(
          canHaveManyInputs = false,
          canBeEnding = false,
          outputs = NonEmptyList.of(ComponentOutput.MainOutput, ComponentOutput.RejectedOutput)
        )
      )
      .withCustom(
        namedMainComponent,
        Some(Unknown),
        CustomComponentSpecificData(
          canHaveManyInputs = false,
          canBeEnding = false,
          outputs = NonEmptyList.of(ComponentOutput("passed"), ComponentOutput.RejectedOutput)
        )
      )
      .withCustom(
        joinComponent,
        Some(Unknown),
        CustomComponentSpecificData(canHaveManyInputs = true, canBeEnding = true)
      )
      .withCustom(
        plainComponent,
        Some(Unknown),
        CustomComponentSpecificData(canHaveManyInputs = false, canBeEnding = false)
      )
      .build

    val edgeTypes = EdgeTypesPreparer.prepareEdgeTypes(definitions.components.components)

    // The built-in entries are covered by the test above.
    edgeTypes.filter(_.componentId.`type` == ComponentType.CustomComponent).toSet shouldBe Set(
      UINodeEdges(
        ComponentId(ComponentType.CustomComponent, joinComponent),
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = true
      ),
      UINodeEdges(
        ComponentId(ComponentType.CustomComponent, componentWithOutputs),
        List(EdgeType.CustomNodeOutput("main"), EdgeType.CustomNodeOutput("rejected")),
        canChooseNodes = false,
        isForInputDefinition = false
      ),
      UINodeEdges(
        ComponentId(ComponentType.CustomComponent, namedMainComponent),
        List(EdgeType.CustomNodeOutput("passed"), EdgeType.CustomNodeOutput("rejected")),
        canChooseNodes = false,
        isForInputDefinition = false
      )
    )
  }

}
