package pl.touk.nussknacker.engine.kafka.generic

import java.nio.charset.StandardCharsets
import java.util
import java.util.Collections

import io.circe.{Decoder, Json, JsonObject}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.connectors.kafka.internals.KafkaDeserializationSchemaWrapper
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.{BaseKafkaSourceFactory, KafkaConfig, KafkaSourceFactory}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.typing.TypingUtils

object sources {

  import collection.JavaConverters._

  class GenericJsonSourceFactory(config: KafkaConfig) extends KafkaSourceFactory[java.util.Map[_, _]](
    config, JsonMapDeserialization, None, TestParsingUtils.newLineSplit)

  class GenericTypedJsonSourceFactory(config: KafkaConfig) extends BaseKafkaSourceFactory[TypedMap](
    config,  None, TestParsingUtils.newLineSplit) {

    @MethodToInvoke
    def create(processMetaData: MetaData,  @ParamName("topic") topic: String,
               @ParamName("type") definition: java.util.Map[String, _]): Source[TypedMap] with TestDataGenerator = {
      val schema = new KafkaDeserializationSchemaWrapper(JsonTypedMapDeserialization)
      new KafkaSource(consumerGroupId = processMetaData.id, List(topic), schema, None) with ReturningType {
        override def returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)
      }
    }
  }

  //FIXME: handle numeric conversion and validation here??
  //how should we treat json that is non-object?
  private def deserializeToMap(message: Array[Byte]): java.util.Map[String, _] =
    toJson(new String(message, StandardCharsets.UTF_8)).asObject.map(jsonObjectToMap).getOrElse(Collections.emptyMap[String, Any])

  private def toJson(jsonString: String): Json = CirceUtil.decodeJsonUnsafe[Json](jsonString, s"invalid message ($jsonString)")

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

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToMap)

  //It is important that object returned by this schema is consistent with types from TypingUtils.typeMapDefinition, i.e. collections type must match etc.
  object JsonTypedMapDeserialization extends EspDeserializationSchema[TypedMap](m => TypedMap(deserializeToMap(m).asScala.toMap))

  //TOOD: better error handling?
  class JsonDecoderDeserialization[T:Decoder:TypeInformation] extends EspDeserializationSchema[T](ba => CirceUtil.decodeJsonUnsafe(ba))

}
