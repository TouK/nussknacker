package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessObjectDependencies}

class ConsumerGroupDeterminer(
    namingStrategy: ConsumerGroupNamingStrategy.Value,
    dependencies: ProcessObjectDependencies
) {

  def consumerGroup(
      processName: ProcessName,
      nodeId: NodeId
  ): String = {
    val baseName = namingStrategy match {
      case ConsumerGroupNamingStrategy.ProcessId       => processName.value
      case ConsumerGroupNamingStrategy.ProcessIdNodeId => processName.value + "-" + nodeId.id
    }
    dependencies.objectNaming.prepareName(baseName, dependencies.config, new NamingContext(KafkaUsageKey))
  }

}
