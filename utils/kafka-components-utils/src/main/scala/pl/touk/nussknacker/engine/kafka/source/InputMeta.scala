package pl.touk.nussknacker.engine.kafka.source

import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object InputMeta {

  val keyParameterName           = "key"
  val topicParameterName         = "topic"
  val partitionParameterName     = "partition"
  val offsetParameterName        = "offset"
  val timestampParameterName     = "timestamp"
  val timestampTypeParameterName = "timestampType"
  val headersParameterName       = "headers"
  val leaderEpochParameterName   = "leaderEpoch"

  /**
   * InputMeta represents kafka event metadata. It is based on [[org.apache.kafka.clients.consumer.ConsumerRecord]].
   * Ignored fields: checksum, serializedKeySize, serializedValueSize.
   *
   * @param key - event key
   * @param topic - kafka topic
   * @param partition - kafka partition
   * @param offset - event offset
   * @param timestamp - event timestamp
   * @param timestampType - see [[org.apache.kafka.common.record.TimestampType]]
   * @param headers - event headers converted to map
   * @param leaderEpoch - number of leaders previously assigned by the controller (> 0 indicates leader failure)
   */
  def apply(
      key: Any,
      topic: String,
      partition: Integer,
      offset: java.lang.Long,
      timestamp: java.lang.Long,
      timestampType: TimestampType,
      headers: java.util.Map[String, String],
      leaderEpoch: Integer
  ): java.util.Map[String, Any] = {
    Map(
      keyParameterName           -> key,
      topicParameterName         -> topic,
      partitionParameterName     -> partition,
      offsetParameterName        -> offset,
      timestampParameterName     -> timestamp,
      timestampTypeParameterName -> timestampType,
      headersParameterName       -> headers,
      leaderEpochParameterName   -> leaderEpoch
    ).asJava
  }

  /**
    * Provides definition of whole metadata object, with given key type definition (keyTypingResult).
    */
  def withType(keyTypingResult: TypingResult): TypingResult = {
    // TODO: exclude non-key parameters to trait BaseKafkaInputMetaVariables and use it in TypesInformationExtractor.mandatoryClasses
    new TypedObjectTypingResult(
      ListMap(
        keyParameterName           -> keyTypingResult,
        topicParameterName         -> Typed[String],
        partitionParameterName     -> Typed[Integer],
        offsetParameterName        -> Typed[java.lang.Long],
        timestampParameterName     -> Typed[java.lang.Long],
        timestampTypeParameterName -> Typed[TimestampType],
        headersParameterName -> Typed
          .genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Typed[String])),
        leaderEpochParameterName -> Typed[Integer]
      ),
      Typed.typedClass[java.util.Map[_, _]]
    ) {
      override def display: String = s"InputMeta[${keyTypingResult.display}]"
    }
  }

}
