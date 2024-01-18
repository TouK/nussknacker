package pl.touk.nussknacker.engine.node

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.PatientScalaFutures

class ComponentInfoExtractorSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  private val componentName = "testComponentName"
  private val componentId   = "testComponentId"

  it should "create ComponentInfo for NodeData" in {
    val testingData = Table(
      ("nodeData", "expected"),
      (Filter("", ""), Some(BuiltInComponentInfo.Filter)),
      (Switch(""), Some(BuiltInComponentInfo.Choice)),
      (VariableBuilder("", "", Nil), Some(BuiltInComponentInfo.RecordVariable)),
      (Variable("", "", ""), Some(BuiltInComponentInfo.Variable)),
      (Split(""), Some(BuiltInComponentInfo.Split)),
      (FragmentInputDefinition("", Nil), Some(BuiltInComponentInfo.FragmentInputDefinition)),
      (FragmentOutputDefinition("", ""), Some(BuiltInComponentInfo.FragmentOutputDefinition)),
      (
        Source("source", SourceRef(componentName, Nil)),
        Some(ComponentInfo(ComponentType.Source, componentName))
      ),
      (Sink("sink", SinkRef(componentName, Nil)), Some(ComponentInfo(ComponentType.Sink, componentName))),
      (
        Enricher("enricher", ServiceRef(componentName, Nil), "out"),
        Some(ComponentInfo(ComponentType.Service, componentName))
      ),
      (
        Processor("processor", ServiceRef(componentName, Nil)),
        Some(ComponentInfo(ComponentType.Service, componentName))
      ),
      (
        CustomNode("custom", None, componentName, Nil),
        Some(ComponentInfo(ComponentType.CustomComponent, componentName))
      ),
      (
        FragmentInput("fragment", FragmentRef(componentName, Nil)),
        Some(ComponentInfo(ComponentType.Fragment, componentName))
      ),
      (FragmentUsageOutput("output", "", None), None),
      (BranchEndData(BranchEndDefinition("", "")), None),
      (Source("source", SourceRef(componentId, Nil)), Some(ComponentInfo(ComponentType.Source, componentId))),
    )

    forAll(testingData) { (nodeData: NodeData, expected: Option[ComponentInfo]) =>
      val result = ComponentInfoExtractor.fromScenarioNode(nodeData)
      result shouldBe expected
    }
  }

}
