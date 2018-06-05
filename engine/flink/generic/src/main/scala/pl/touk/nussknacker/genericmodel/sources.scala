package pl.touk.nussknacker.genericmodel

import java.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.{BaseKafkaSourceFactory, KafkaConfig, KafkaSourceFactory}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import scala.collection.JavaConversions._

//TODO: consider moving to flink-util? have to add jackson dependency then...
object sources {

  private val objectMapper = new ObjectMapper()

  class GenericJsonSourceFactory(config: KafkaConfig) extends KafkaSourceFactory[java.util.Map[_, _]](config, JsonMapDeserialization, None, TestParsingUtils.newLineSplit)

  class GenericTypedJsonSourceFactory(config: KafkaConfig) extends BaseKafkaSourceFactory[TypedMap](config, JsonTypedMapDeserializaion, None, TestParsingUtils.newLineSplit) {

    @MethodToInvoke
    def create(processMetaData: MetaData,  @ParamName("topic") topic: String, @ParamName("type") definition: java.util.Map[String, _]): Source[TypedMap] with TestDataGenerator = {
      new KafkaSource(consumerGroupId = processMetaData.id, topic) with ReturningType {
        override def returnType: typing.TypingResult = TypingUtils.typedMapDefinitionFromParameters(definition)
      }
    }
  }

  private def deserializeToJMap(message: Array[Byte]): util.Map[String, _] = objectMapper.readValue(message, classOf[java.util.Map[String, _]])

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToJMap)

  object JsonTypedMapDeserializaion extends EspDeserializationSchema[TypedMap](m => TypedMap(deserializeToJMap(m).toMap))

}
