package pl.touk.nussknacker.ui.component

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.test.PatientScalaFutures

class DefaultComponentIdProviderSpec extends AnyFlatSpec with Matchers with PatientScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val componentNameToOverride = "componentNameToOverride"
  private val processingType          = "testProcessingType"
  private val componentName           = "testComponentName"

  private val overriddenId = ComponentId("overriddenId")

  private val componentIdProvider = new DefaultComponentIdProvider(
    Map(
      processingType -> ComponentsUiConfig(
        Map(
          componentNameToOverride -> SingleComponentConfig.zero.copy(componentId = Some(overriddenId))
        )
      )
    )
  )

  private val notBuiltIntComponents = ComponentType.NotBuiltInComponentTypes.map(ComponentInfo(_, componentName))

  it should "create ComponentId" in {
    val testingData = Table(
      ("componentsInfo", "expectedComponentId"),
      (BuiltInComponentInfo.All, BuiltInComponentInfo.All.map(cid)),
      (notBuiltIntComponents, notBuiltIntComponents.map(cid)),
      (
        notBuiltIntComponents
          .filter(_.`type` != ComponentType.Fragment)
          .map(_.copy(name = componentNameToOverride)),
        notBuiltIntComponents.filter(_.`type` != ComponentType.Fragment).map(_ => overriddenId)
      ),
    )

    forAll(testingData) { (componentInfo: List[ComponentInfo], expected: List[ComponentId]) =>
      val result = componentInfo.map(componentIdProvider.createComponentId(processingType, _))
      result shouldBe expected
    }
  }

  it should "throw exception when forbidden overriding is detected" in {
    val componentsWithRestrictedType =
      BuiltInComponentInfo.All ++ List(ComponentInfo(ComponentType.Fragment, componentName))

    val provider = new DefaultComponentIdProvider(
      Map(
        processingType -> ComponentsUiConfig(
          componentsWithRestrictedType
            .map(_.name -> SingleComponentConfig.zero.copy(componentId = Some(overriddenId)))
            .toMap
        )
      )
    )

    componentsWithRestrictedType.foreach(componentInfo => {
      intercept[IllegalArgumentException] {
        provider.createComponentId(processingType, componentInfo)
      }.getMessage shouldBe s"Component id can't be overridden for: $componentInfo"
    })
  }

  // TODO: This test should be moved next to ComponentInfoExtractor and here we should test only component id overriding mechanism
  it should "create ComponentId for NodeData" in {
    val testingData = Table(
      ("nodeData", "expected"),
      (Filter("", ""), Some(cid(BuiltInComponentInfo.Filter))),
      (Switch(""), Some(cid(BuiltInComponentInfo.Choice))),
      (VariableBuilder("", "", Nil), Some(cid(BuiltInComponentInfo.RecordVariable))),
      (Variable("", "", ""), Some(cid(BuiltInComponentInfo.Variable))),
      (Split(""), Some(cid(BuiltInComponentInfo.Split))),
      (FragmentInputDefinition("", Nil), Some(cid(BuiltInComponentInfo.FragmentInputDefinition))),
      (FragmentOutputDefinition("", ""), Some(cid(BuiltInComponentInfo.FragmentOutputDefinition))),
      (
        Source("source", SourceRef(componentName, Nil)),
        Some(cid(ComponentInfo(ComponentType.Source, componentName)))
      ),
      (Sink("sink", SinkRef(componentName, Nil)), Some(cid(ComponentInfo(ComponentType.Sink, componentName)))),
      (
        Enricher("enricher", ServiceRef(componentName, Nil), "out"),
        Some(cid(ComponentInfo(ComponentType.Service, componentName)))
      ),
      (
        Processor("processor", ServiceRef(componentName, Nil)),
        Some(cid(ComponentInfo(ComponentType.Service, componentName)))
      ),
      (
        CustomNode("custom", None, componentName, Nil),
        Some(cid(ComponentInfo(ComponentType.CustomComponent, componentName)))
      ),
      (
        FragmentInput("fragment", FragmentRef(componentName, Nil)),
        Some(cid(ComponentInfo(ComponentType.Fragment, componentName)))
      ),
      (FragmentUsageOutput("output", "", None), None),
      (BranchEndData(BranchEndDefinition("", "")), None),
      (Source("source", SourceRef(componentNameToOverride, Nil)), Some(overriddenId)),
    )

    forAll(testingData) { (nodeData: NodeData, expected: Option[ComponentId]) =>
      val result = componentIdProvider.nodeToComponentId(processingType, nodeData)
      result shouldBe expected
    }
  }

  private def cid(componentInfo: ComponentInfo): ComponentId =
    ComponentId.default(processingType, componentInfo)
}
