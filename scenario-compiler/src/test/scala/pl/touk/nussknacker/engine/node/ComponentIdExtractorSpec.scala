package pl.touk.nussknacker.engine.node

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.PatientScalaFutures

class ComponentIdExtractorSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  private val componentName = "testComponentName"
  private val componentId   = "testComponentId"

  it should "create ComponentId for NodeData" in {
    val testingData = Table(
      ("nodeData", "expected"),
      (Filter("", "".spel), Some(BuiltInComponentId.Filter)),
      (Switch(""), Some(BuiltInComponentId.Choice)),
      (VariableBuilder("", "", Nil), Some(BuiltInComponentId.RecordVariable)),
      (Variable("", "", "".spel), Some(BuiltInComponentId.Variable)),
      (Split(""), Some(BuiltInComponentId.Split)),
      (FragmentInputDefinition("", Nil), Some(BuiltInComponentId.FragmentInputDefinition)),
      (FragmentOutputDefinition("", ""), Some(BuiltInComponentId.FragmentOutputDefinition)),
      (
        Source("source", SourceRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Source, componentName))
      ),
      (Sink("sink", SinkRef(componentName, Nil)), Some(ComponentId(ComponentType.Sink, componentName))),
      (
        Enricher("enricher", ServiceRef(componentName, Nil), "out"),
        Some(ComponentId(ComponentType.Service, componentName))
      ),
      (
        Processor("processor", ServiceRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Service, componentName))
      ),
      (
        CustomNode("custom", None, componentName, Nil),
        Some(ComponentId(ComponentType.CustomComponent, componentName))
      ),
      (
        FragmentInput("fragment", FragmentRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Fragment, componentName))
      ),
      (FragmentUsageOutput("output", "", None), None),
      (BranchEndData(BranchEndDefinition("", "")), None),
      (Source("source", SourceRef(componentId, Nil)), Some(ComponentId(ComponentType.Source, componentId))),
    )

    forAll(testingData) { (nodeData: NodeData, expected: Option[ComponentId]) =>
      val result = ComponentIdExtractor.fromScenarioNode(nodeData)
      result shouldBe expected
    }
  }

}
