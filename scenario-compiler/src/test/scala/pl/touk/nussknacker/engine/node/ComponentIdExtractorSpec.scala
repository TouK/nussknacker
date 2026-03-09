package pl.touk.nussknacker.engine.node

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
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
      (Filter(NodeId(""), NodeName(""), "".spel), Some(BuiltInComponentId.Filter)),
      (Switch(NodeId(""), NodeName(""), None, None), Some(BuiltInComponentId.Choice)),
      (VariableBuilder(NodeId(""), NodeName(""), "", Nil), Some(BuiltInComponentId.RecordVariable)),
      (Variable(NodeId(""), NodeName(""), "", "".spel), Some(BuiltInComponentId.Variable)),
      (Split(NodeId(""), NodeName("")), Some(BuiltInComponentId.Split)),
      (FragmentInputDefinition(NodeId(""), NodeName(""), Nil), Some(BuiltInComponentId.FragmentInputDefinition)),
      (FragmentOutputDefinition(NodeId(""), NodeName(""), ""), Some(BuiltInComponentId.FragmentOutputDefinition)),
      (
        Source(NodeId("source"), NodeName("source"), SourceRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Source, componentName))
      ),
      (
        Sink(NodeId("sink"), NodeName("sink"), SinkRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Sink, componentName))
      ),
      (
        Enricher(NodeId("enricher"), NodeName("enricher"), ServiceRef(componentName, Nil), "out"),
        Some(ComponentId(ComponentType.Service, componentName))
      ),
      (
        Processor(NodeId("processor"), NodeName("processor"), ServiceRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Service, componentName))
      ),
      (
        CustomNode(NodeId("custom"), NodeName("custom"), None, componentName, Nil),
        Some(ComponentId(ComponentType.CustomComponent, componentName))
      ),
      (
        FragmentInput(NodeId("fragment"), NodeName("fragment"), FragmentRef(componentName, Nil)),
        Some(ComponentId(ComponentType.Fragment, componentName))
      ),
      (FragmentUsageOutput(NodeId("output"), NodeName("output"), NodeId("fragmentId"), "", None), None),
      (BranchEndData(BranchEndDefinition("", "")), None),
      (
        Source(NodeId("source"), NodeName("source"), SourceRef(componentId, Nil)),
        Some(ComponentId(ComponentType.Source, componentId))
      ),
    )

    forAll(testingData) { (nodeData: NodeData, expected: Option[ComponentId]) =>
      val result = ComponentIdExtractor.fromScenarioNode(nodeData)
      result shouldBe expected
    }
  }

}
