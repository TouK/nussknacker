package pl.touk.nussknacker.engine.kafka.generic

import io.circe.{Decoder, Json, JsonObject}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.serialization.NKKafkaDeserializationSchemaWrapper
import pl.touk.nussknacker.engine.kafka.source.{KafkaSource, KafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.{BasicRecordFormatter, KafkaConfig, KafkaUtils, RecordFormatter, RecordFormatterFactory}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import java.nio.charset.StandardCharsets
import java.util
import java.util.Collections

//TODO: Move it to source package
object sources {

  import collection.JavaConverters._

  class GenericJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, java.util.Map[_, _]](
    new FixedValueDeserializationSchemaFactory(JsonMapDeserialization), None, FixedRecordFormatterFactoryWrapper(JsonRecordFormatter), processObjectDependencies)

  class GenericTypedJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies)
    extends FlinkSourceFactory[TypedMap] with Serializable {

    @MethodToInvoke
    def create(@ParamName("topic") topic: String, @ParamName("type") definition: java.util.Map[String, _]): Source[TypedMap] with TestDataGenerator = {
      val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
      val deserializationSchema = new NKKafkaDeserializationSchemaWrapper(JsonTypedMapDeserialization)
      val preparedTopics = List(KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies))
      new KafkaSource(preparedTopics, kafkaConfig, deserializationSchema, None, JsonRecordFormatter) with ReturningType {
        override def returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)
      }
    }
  }

  //FIXME: handle numeric conversion and validation here??
  //how should we treat json that is non-object?
  private def deserializeToMap(message: Array[Byte]): java.util.Map[String, _] =
    toJson(message).asObject.map(jsonObjectToMap).getOrElse(Collections.emptyMap[String, Any])

  private def toJson(jsonBytes: Array[Byte]): Json = {
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

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToMap)

  //It is important that object returned by this schema is consistent with types from TypingUtils.typeMapDefinition, i.e. collections type must match etc.
  object JsonTypedMapDeserialization extends EspDeserializationSchema[TypedMap](m => TypedMap(deserializeToMap(m).asScala.toMap))

  //TOOD: better error handling?
  class JsonDecoderDeserialization[T:Decoder:TypeInformation] extends EspDeserializationSchema[T](ba => CirceUtil.decodeJsonUnsafe(ba))

  //We format before returning to user, to avoid problems with empty lines etc.
  object JsonRecordFormatter extends RecordFormatter {

    private val basicRecordFormatter = BasicRecordFormatter(TestParsingUtils.emptyLineSplit)

    override def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] =
      toJson(record.value()).spaces2.getBytes(StandardCharsets.UTF_8)

    override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] =
      basicRecordFormatter.parseRecord(topic, bytes)

    override def testDataSplit: TestDataSplit =
      basicRecordFormatter.testDataSplit
  }

  object FixedRecordFormatterFactoryWrapper {
    def apply(formatter: RecordFormatter): RecordFormatterFactory = new RecordFormatterFactory {
      override def create[K, V](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = formatter
    }
  }

}
