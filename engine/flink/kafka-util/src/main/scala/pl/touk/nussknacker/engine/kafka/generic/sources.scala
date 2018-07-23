package pl.touk.nussknacker.engine.kafka.generic

import argonaut.{Json, JsonObject}
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.{BaseKafkaSourceFactory, KafkaConfig, KafkaSourceFactory}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

object sources {

  class GenericJsonSourceFactory(config: KafkaConfig) extends KafkaSourceFactory[java.util.Map[_, _]](config, JsonMapDeserialization, None, TestParsingUtils.newLineSplit)

  class GenericTypedJsonSourceFactory(config: KafkaConfig) extends BaseKafkaSourceFactory[TypedMap](config, JsonTypedMapDeserializaion, None, TestParsingUtils.newLineSplit) {

    @MethodToInvoke
    def create(processMetaData: MetaData,  @ParamName("topic") topic: String, @ParamName("type") definition: java.util.Map[String, _]): Source[TypedMap] with TestDataGenerator = {
      new KafkaSource(consumerGroupId = processMetaData.id, List(topic)) with ReturningType {
        override def returnType: typing.TypingResult = TypingUtils.typedMapDefinitionFromParameters(definition)
      }
    }
  }

  //FIXME: handle numeric conversion and validation here??
  private def deserializeToMap(message: Array[Byte]): Map[String, _] = jsonToMap(toJson(new String(message)).objectOrEmpty)

  private def toJson(jsonString: String): Json = {
    argonaut.Parse.parse(jsonString) match {
      case Left(e) =>
        throw new RuntimeException(s"Cannot parse json. Reason: $e, input string: $jsonString")
      case Right(j) =>
        j
    }
  }

  private def jsonToMap(jo: JsonObject): Map[String, _] = {
    jo.toMap.mapValuesNow { jsonField =>
      jsonField.fold(
        jsonNull = null,
        jsonBool = identity,
        jsonNumber = _.truncateToLong,
        jsonString = identity,
        jsonArray = _.map(f => jsonToMap(f.objectOrEmpty)).asJava,
        jsonObject = jsonToMap
      )
    }
  }

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToMap)

  object JsonTypedMapDeserializaion extends EspDeserializationSchema[TypedMap](m => TypedMap(deserializeToMap(m)))

}
