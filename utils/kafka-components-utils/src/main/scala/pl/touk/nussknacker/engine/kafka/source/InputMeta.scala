package pl.touk.nussknacker.engine.kafka.source

import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import scala.collection.immutable.ListMap
import scala.collection.mutable
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
    val map = new java.util.HashMap[String, Any]()
    map.put(keyParameterName, key)
    map.put(topicParameterName, topic)
    map.put(partitionParameterName, partition)
    map.put(offsetParameterName, offset)
    map.put(timestampParameterName, timestamp)
    map.put(timestampTypeParameterName, timestampType)
    map.put(headersParameterName, headers)
    map.put(leaderEpochParameterName, leaderEpoch)
    map
  }

  /**
    * Provides definition of whole metadata object, with given key type definition (keyTypingResult).
    */
  def withType(keyTypingResult: TypingResult): TypingResult = {
    // TODO: exclude non-key parameters to trait BaseKafkaInputMetaVariables and use it in TypesInformationExtractor.mandatoryClasses
    // TODO: add displayStrategy similar to this in Unknown type instead of extending case class
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
