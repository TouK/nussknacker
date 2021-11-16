package pl.touk.nussknacker.engine.kafka.serialization

import io.circe.{Encoder, Json, JsonObject}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatterFactory
import pl.touk.nussknacker.engine.util.Implicits._

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.{lang, util}

object schemas {

  private def safeBytes(value: String): Array[Byte] = Option(value).map(_.getBytes(StandardCharsets.UTF_8)).orNull

  //trait mostly for making java version more usable (lambda <-> function serializability problems,
  // the fact we cross compile to 2.11 only makes it worse...)
  trait ToStringSerializer[T] extends Serializable {
    def serialize(value: T): String
  }

  object ToStringSerializer {
    def apply[T](fun: T => String): ToStringSerializer[T] = new ToStringSerializer[T] {
      override def serialize(value: T): String = fun(value)
    }
  }

  trait ToHeadersSerializer[T] extends Serializable {
    def serialize(value: T): Headers
  }
  object ToHeaderMapSerializer {
    def apply[T](fun: T => Headers): ToHeadersSerializer[T] = new ToHeadersSerializer[T] {
      override def serialize(value: T): Headers = fun(value)
    }
  }

  class BaseSimpleSerializationSchema[T](topic: String,
                                         valueSerializer: ToStringSerializer[T],
                                         keySerializer: ToStringSerializer[T],
                                         headersSerializer: ToHeadersSerializer[T])
    extends KafkaSerializationSchema[T] {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null, headersSerializer: T => Headers = (_: T) => ConsumerRecordUtils.emptyHeaders) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer), ToHeaderMapSerializer(headersSerializer))
    }

    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val value = valueSerializer.serialize(element)
      val key = Option(keySerializer).map(_.serialize(element)).orNull
      val headers = Option(headersSerializer).map(_.serialize(element)).getOrElse(ConsumerRecordUtils.emptyHeaders)
      KafkaProducerHelper.createRecord(topic, safeBytes(key), safeBytes(value), timestamp, headers)
    }
  }

  class SimpleSerializationSchema[T](topic: String,
                                     valueSerializer: ToStringSerializer[T],
                                     keySerializer: ToStringSerializer[T])
    extends BaseSimpleSerializationSchema[T](topic, valueSerializer, keySerializer, null) {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer))
    }

  }

  class JsonSerializationSchema[T: Encoder](topic: String, keySerializer: T => String = (_: T) => null)
    extends SimpleSerializationSchema[T](topic, ToStringSerializer[T](v => implicitly[Encoder[T]].apply(v).noSpaces), ToStringSerializer[T](keySerializer))

  // deserialization

  import collection.JavaConverters._

  def jsonFormatterFactory = new ConsumerRecordToJsonFormatterFactory[Json, Json]()

  //It is important that object returned by this schema is consistent with types from TypingUtils.typeMapDefinition, i.e. collections type must match etc.
  def deserializeToTypedMap(message: Array[Byte]): TypedMap = TypedMap(deserializeToMap(message).asScala.toMap)

  //FIXME: handle numeric conversion and validation here??
  //how should we treat json that is non-object?
  def deserializeToMap(message: Array[Byte]): java.util.Map[String, _] =
    toJson(message).asObject.map(jsonObjectToMap).getOrElse(Collections.emptyMap[String, Any])

  def toJson(jsonBytes: Array[Byte]): Json = {
    val value = new String(jsonBytes, StandardCharsets.UTF_8)
    CirceUtil.decodeJsonUnsafe[Json](value, s"invalid message ($value)")
  }

  private def jsonToMap(jo: Json): Any = jo.fold(
    jsonNull = null,
    jsonBoolean = identity,
    //TODO: how to handle fractions here? using BigDecimal is not always good way to go...
    jsonNumber = number => {
      val d = number.toDouble
      if (d.isWhole()) d.toLong else d
    },
    jsonString = identity,
    jsonArray = _.map(jsonToMap).asJava,
    jsonObject = jsonObjectToMap
  )

  private def jsonObjectToMap(jo: JsonObject): util.Map[String, Any] = jo.toMap.mapValuesNow(jsonToMap).asJava

}
