package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

object NodeComponentInfo {

  // TODO: remove this method - in every place of usage, should be accessible ComponentId
  def apply(nodeId: String, componentType: ComponentType, componentName: String): NodeComponentInfo = {
    NodeComponentInfo(nodeId, Some(ComponentId(componentType, componentName)))
  }

}

// componentId is not used anywhere in this repo - it is only for external project's purpose for now
// TODO: Remove Option from componentId - every Node used in runtime is related with some Component. After doing it, we should
//       report this id in KafkaExceptionInfo - see KafkaJsonExceptionSerializationSchema
final case class NodeComponentInfo(nodeId: String, componentId: Option[ComponentId])
