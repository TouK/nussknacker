package pl.touk.nussknacker.engine.kafka.generic

import java.nio.charset.StandardCharsets

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
  private def deserializeToMap(message: Array[Byte]): Map[String, _] = jsonToMap(toJson(new String(message, StandardCharsets.UTF_8)))

  private def toJson(jsonString: String): Json = CirceUtil.decodeJsonUnsafe[Json](jsonString, s"invalid message ($jsonString)")

  private def jsonToMap(jo: Json): Map[String, _] = {
    jo.asObject.getOrElse(JsonObject()).toMap.mapValuesNow { jsonField =>
      jsonField.fold(
        jsonNull = null,
        jsonBoolean = identity,
        //TODO: how to handle fractions here? using BigDecimal is not always good way to go...
        jsonNumber = number => {
          val d = number.toDouble
          if (d.isWhole()) d.toLong else d
        },
        jsonString = identity,
        jsonArray = _.map(f => jsonToMap(f).asJava),
        jsonObject = jo => jsonToMap(Json.fromJsonObject(jo))
      )
    }
  }

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](m => deserializeToMap(m).asJava)

  object JsonTypedMapDeserialization extends EspDeserializationSchema[TypedMap](m => TypedMap(deserializeToMap(m)))

  //TOOD: better error handling?
  class JsonDecoderDeserialization[T:Decoder:TypeInformation] extends EspDeserializationSchema[T](ba => CirceUtil.decodeJsonUnsafe(ba))

}
