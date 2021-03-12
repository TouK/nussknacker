package pl.touk.nussknacker.engine.kafka.consumerrecord

case class DeserializedConsumerRecord[K,V](value: V, key: Option[K], topic: String, partition: Int, offset: Long, timestamp: Long, headers: Map[String, Option[String]])
