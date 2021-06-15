package pl.touk.nussknacker.engine.kafka.consumerrecord

/**
  * Wrapper for ConsumerRecord fields used for test data serialization, eg. json serialization.
  * All fields apart from value are optional.
  */
trait SerializableConsumerRecord[K, V] {
  val key: Option[K]
  val value: V
  val topic: Option[String]
  val partition: Option[Int]
  val offset: Option[Long]
  val timestamp: Option[Long]
  val timestampType: Option[String]
  val headers: Option[Map[String, Option[String]]]
  val leaderEpoch: Option[Int]
}

