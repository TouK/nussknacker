package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.component.ComponentInfoExtractor
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor.ComponentsUiConfig
import pl.touk.nussknacker.engine.graph.node.NodeData

//TODO: It is work around for components duplication across multiple scenario types, until we figure how to do deduplication.
trait ComponentIdProvider {
  def createComponentId(processingType: String, componentInfo: ComponentInfo): ComponentId
  def nodeToComponentId(processingType: String, node: NodeData): Option[ComponentId]
}

class DefaultComponentIdProvider(configs: Map[String, ComponentsUiConfig]) extends ComponentIdProvider {

  private val RestrictedComponentTypes = Set(ComponentType.BuiltIn, ComponentType.Fragment)

  override def createComponentId(
      processingType: String,
      componentInfo: ComponentInfo
  ): ComponentId = {
    val defaultComponentId = ComponentId.default(processingType, componentInfo)
    val overriddenComponentId =
      getOverriddenComponentId(processingType, componentInfo.name, defaultComponentId)

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
      processingType: String,
      componentName: String,
      defaultComponentId: ComponentId
  ): ComponentId = {
    def getComponentId(name: String): Option[ComponentId] =
      configs.get(processingType).flatMap(_.get(name)).flatMap(_.componentId)

    val componentId = getComponentId(componentName)

    // It's work around for components with the same name and different componentType, eg. kafka-avro
    // where default id is combination of processingType-componentType-name
    val componentIdForDefaultComponentId = getComponentId(defaultComponentId.value)

    componentId
      .orElse(componentIdForDefaultComponentId)
      .getOrElse(defaultComponentId)
  }

}
