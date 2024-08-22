package pl.touk.nussknacker.engine.kafka.source

import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.{Encoder, Json}
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.util.json.ToJsonEncoderCustomisation

import scala.collection.immutable.ListMap

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
  * @tparam K - type of event key
  */
case class InputMeta[K](
    key: K,
    topic: String,
    partition: Integer,
    offset: java.lang.Long,
    timestamp: java.lang.Long,
    timestampType: TimestampType,
    headers: java.util.Map[String, String],
    leaderEpoch: Integer
)

object InputMeta {

  val keyParameterName: String = "key"

  /**
    * Provides definition of whole metadata object, with given key type definition (keyTypingResult).
    */
  def withType(keyTypingResult: TypingResult): TypingResult = {
    // TODO: exclude non-key parameters to trait BaseKafkaInputMetaVariables and use it in TypesInformationExtractor.mandatoryClasses
    new TypedObjectTypingResult(
      ListMap(
        keyParameterName -> keyTypingResult,
        "topic"          -> Typed[String],
        "partition"      -> Typed[Integer],
        "offset"         -> Typed[java.lang.Long],
        "timestamp"      -> Typed[java.lang.Long],
        "timestampType"  -> Typed[TimestampType],
        "headers"        -> Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Typed[String])),
        "leaderEpoch"    -> Typed[Integer]
      ),
      Typed.typedClass[InputMeta[AnyRef]]
    ) {
      override def display: String = Typed.genericTypeClass(classOf[InputMeta[_]], List(keyTypingResult)).display
    }
  }

}

class InputMetaToJsonCustomisation extends ToJsonEncoderCustomisation {

  import pl.touk.nussknacker.engine.api.CirceUtil._
  import pl.touk.nussknacker.engine.api.CirceUtil.codecs._

  private implicit val timeEncoder: Encoder[TimestampType] = Encoder.instance(k => io.circe.Json.fromString(k.toString))

  private val forJsonKey: Encoder[InputMeta[Json]] = deriveConfiguredEncoder

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case a: InputMeta[_] =>
    forJsonKey(a.copy(key = encode(a.key)))
  }

}
