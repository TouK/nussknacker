package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

class ConsumerGroupDeterminer(consumerGroupNamingStrategy: ConsumerGroupNamingStrategy.Value) {

  def consumerGroup(nodeContext: FlinkCustomNodeContext): String = {
    consumerGroup(nodeContext.metaData.name, nodeContext.nodeId)
  }

  def consumerGroup(processName: ProcessName, nodeId: NodeId): String = {
    consumerGroupNamingStrategy match {
      case ConsumerGroupNamingStrategy.ProcessId       => processName.value
      case ConsumerGroupNamingStrategy.ProcessIdNodeId => processName.value + "-" + nodeId.value
    }
  }

}

object ConsumerGroupDeterminer {

  def apply(config: KafkaComponentsConfig): ConsumerGroupDeterminer =
    new ConsumerGroupDeterminer(
      config.consumerGroupNamingStrategy.getOrElse(ConsumerGroupNamingStrategy.ProcessIdNodeId)
    )

}
