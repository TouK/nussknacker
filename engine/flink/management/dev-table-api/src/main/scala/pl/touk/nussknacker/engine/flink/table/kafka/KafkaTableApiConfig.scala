package pl.touk.nussknacker.engine.flink.table.kafka

/**
 * TODO: here should be for example:
 *  - schema
 *  - data format
 *  - topic
 */
final case class KafkaTableApiConfig(kafkaProperties: Map[String, String] = Map.empty, topic: String)
