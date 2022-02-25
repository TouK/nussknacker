package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

class ConsumerGroupDeterminer(consumerGroupNamingStrategy: ConsumerGroupNamingStrategy.Value) {

  def consumerGroup(nodeContext: FlinkCustomNodeContext): String = {
    consumerGroup(nodeContext.metaData.id, nodeContext.nodeId)
  }

  def consumerGroup(processId: String, nodeId: String): String = {
    consumerGroupNamingStrategy match {
      case ConsumerGroupNamingStrategy.ProcessId => processId
      case ConsumerGroupNamingStrategy.ProcessIdNodeId => processId + "-" + nodeId
    }
  }

}

object ConsumerGroupDeterminer {

  def apply(config: KafkaConfig): ConsumerGroupDeterminer =
    new ConsumerGroupDeterminer(config.consumerGroupNamingStrategy.getOrElse(ConsumerGroupNamingStrategy.ProcessIdNodeId))

}
