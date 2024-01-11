package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.node.ComponentInfoExtractor

//TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
trait ComponentIdProvider {
  def createComponentId(processingType: ProcessingType, componentInfo: ComponentInfo): ComponentId
  def nodeToComponentId(processingType: ProcessingType, node: NodeData): Option[ComponentId]
}

class DefaultComponentIdProvider(
    configByProcessingTypeAndInfo: (ProcessingType, ComponentInfo) => Option[SingleComponentConfig]
) extends ComponentIdProvider {

  private val RestrictedComponentTypes = Set(ComponentType.BuiltIn, ComponentType.Fragment)

  override def createComponentId(
      processingType: String,
      componentInfo: ComponentInfo
  ): ComponentId = {
    val defaultComponentId = ComponentId.default(processingType, componentInfo)
    val overriddenComponentId =
      getOverriddenComponentId(processingType, componentInfo, defaultComponentId)

    // We assume that base and currently fragment component's id can't be overridden
    if (defaultComponentId != overriddenComponentId && RestrictedComponentTypes.contains(componentInfo.`type`)) {
      throw new IllegalArgumentException(
        s"Component id can't be overridden for: $componentInfo"
      )
    }

    overriddenComponentId
  }

  override def nodeToComponentId(processingType: String, node: NodeData): Option[ComponentId] =
    ComponentInfoExtractor
      .fromScenarioNode(node)
      .map(createComponentId(processingType, _))

  private def getOverriddenComponentId(
      processingType: ProcessingType,
      info: ComponentInfo,
      defaultComponentId: ComponentId
  ): ComponentId = {
    configByProcessingTypeAndInfo(processingType, info)
      .flatMap(_.componentId)
      .getOrElse(defaultComponentId)
  }

}
