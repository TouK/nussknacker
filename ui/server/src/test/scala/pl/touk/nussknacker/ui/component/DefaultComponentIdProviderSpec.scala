package pl.touk.nussknacker.ui.component

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.test.PatientScalaFutures

class DefaultComponentIdProviderSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.spel.Implicits._


  private val processingType = "testProcessingType"
  private val componentName = "testComponentName"
  private val componentFilterName = "filter"
  private val componentSubprocessName = "someSubprocess"
  private val componentNameToOverride = "componentNameToOverride"

  private val overriddenId = ComponentId("overriddenId")

  private val componentIdProvider = new DefaultComponentIdProvider(Map(
    processingType -> Map(
      componentNameToOverride -> SingleComponentConfig.zero.copy(componentId = Some(overriddenId)),
      componentFilterName -> SingleComponentConfig.zero.copy(componentId = Some(overriddenId)),
      componentSubprocessName -> SingleComponentConfig.zero.copy(componentId = Some(overriddenId))
    )
  ))

  private val baseComponentsType = List(
    ComponentType.Filter, ComponentType.Split, ComponentType.Switch, ComponentType.Variable,
    ComponentType.MapVariable, ComponentType.FragmentInput, ComponentType.FragmentOutput
  )

  private val componentsType = List(
    ComponentType.Processor, ComponentType.Enricher, ComponentType.Sink, ComponentType.Source,
    ComponentType.CustomNode, ComponentType.Fragments,
  )

  it should "create ComponentId" in {
    val subprocessId = ComponentId("testprocessingtype-fragments-componentnametooverride")
    val testingData = Table(
      ("componentsType", "name", "expected"),
      (baseComponentsType, componentName, baseComponentsType.map(cid)),
      (baseComponentsType, componentNameToOverride, baseComponentsType.map(cid)),
      (componentsType, componentName, componentsType.map(cid)),
      (componentsType, componentNameToOverride, componentsType.filter(_ != ComponentType.Fragments).map(_ => overriddenId) ++ List(subprocessId)),
    )

    forAll(testingData) { (componentsType: List[ComponentType], name: String, expected: List[ComponentId]) =>
      val result = componentsType.map(componentIdProvider.createComponentId(processingType, name, _))
      result shouldBe expected
    }
  }

  it should "create ComponentId for NodeData" in {
    val testingData = Table(
      ("nodeData", "expected"),
      (Filter(componentName, ""), Some(cid(ComponentType.Filter))),
      (Switch(componentName, "", ""), Some(cid(ComponentType.Switch))),
      (VariableBuilder(componentName, "", Nil), Some(cid(ComponentType.MapVariable))),
      (Variable(componentName, "", ""), Some(cid(ComponentType.Variable))),
      (Split(componentName), Some(cid(ComponentType.Split))),
      (SubprocessInputDefinition(componentName, Nil), Some(cid(ComponentType.FragmentInput))),
      (SubprocessOutputDefinition(componentName, ""), Some(cid(ComponentType.FragmentOutput))),

      (Source("source", SourceRef(componentName, Nil)), Some(cid(ComponentType.Source))),
      (Sink("sink", SinkRef(componentName, Nil)), Some(cid(ComponentType.Sink))),
      (Enricher("enricher", ServiceRef(componentName, Nil), "out"), Some(cid(ComponentType.Enricher))),
      (Processor("processor", ServiceRef(componentName, Nil)), Some(cid(ComponentType.Processor))),
      (CustomNode("custom", None, componentName, Nil), Some(cid(ComponentType.CustomNode))),
      (SubprocessInput("subprocess", SubprocessRef(componentName, Nil)), Some(cid(ComponentType.Fragments))),

      (SubprocessOutput("output", componentName, Nil), None),
      (BranchEndData(BranchEndDefinition("", "")), None),

      (Source("source", SourceRef(componentNameToOverride, Nil)), Some(overriddenId)),
    )

    forAll(testingData) { (nodeData: NodeData, expected: Option[ComponentId]) =>
      val result = componentIdProvider.nodeToComponentId(processingType, nodeData)
      result shouldBe expected
    }
  }

  private def cid(componentType: ComponentType): ComponentId =
    ComponentId.default(processingType, componentName, componentType)
}
