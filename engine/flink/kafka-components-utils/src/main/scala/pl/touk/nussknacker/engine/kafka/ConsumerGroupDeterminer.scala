package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

class ConsumerGroupDeterminer(consumerGroupNamingStrategy: ConsumerGroupNamingStrategy.Value) {

  def consumerGroup(nodeContext: FlinkCustomNodeContext): String = {
    consumerGroup(nodeContext.metaData.name, nodeContext.nodeId)
  }

  def consumerGroup(processName: ProcessName, nodeId: String): String = {
    consumerGroupNamingStrategy match {
      case ConsumerGroupNamingStrategy.ProcessId       => processName.value
      case ConsumerGroupNamingStrategy.ProcessIdNodeId => processName.value + "-" + nodeId
    }
  }

}

object ConsumerGroupDeterminer {

  def apply(config: KafkaConfig): ConsumerGroupDeterminer =
    new ConsumerGroupDeterminer(
      config.consumerGroupNamingStrategy.getOrElse(ConsumerGroupNamingStrategy.ProcessIdNodeId)
    )

}
