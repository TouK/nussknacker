package pl.touk.nussknacker.engine.kafka.generic

import cats.data.Validated.{Invalid, Valid}
import io.circe.{Decoder, Json, JsonObject}
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordToJsonFormatterFactory, FixedValueDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.generic.KafkaDelayedSourceFactory._
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory._
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.{BasicRecordFormatter, KafkaConfig, PreparedKafkaTopic, RecordFormatter, RecordFormatterFactory}
import pl.touk.nussknacker.engine.util.Implicits._

import java.nio.charset.StandardCharsets
import java.util
import java.util.Collections
import scala.reflect.ClassTag

//TODO: Move it to source package
object sources {

  import collection.JavaConverters._

  private def jsonFormatterFactory = new ConsumerRecordToJsonFormatterFactory[Json, Json]()

  class GenericJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, java.util.Map[_, _]](
    new FixedValueDeserializationSchemaFactory(JsonMapDeserialization), None, jsonFormatterFactory, processObjectDependencies)

  class GenericTypedJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, TypedMap](
    new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization), None, jsonFormatterFactory, processObjectDependencies) {

    override protected def prepareInitialParameters: List[Parameter] = super.prepareInitialParameters ++ List(
      TypeParameter
    )

    override protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
      case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (TypeDefinitionParamName, DefinedEagerParameter(definition: Any, _)) :: Nil, _) =>
        val topicValidationErrors = topicsValidationErrors(topic)
        calculateTypingResult(definition) match {
          case Valid((_, typingResult)) =>
            prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, typingResult, topicValidationErrors)
          case Invalid(exc) =>
            val errors = topicValidationErrors ++ List(exc.toCustomNodeError(nodeId))
            prepareSourceFinalErrors(context, dependencies, step.parameters, errors)
        }
      case step@TransformationStep((TopicParamName, top) :: (TypeDefinitionParamName, typ) :: Nil, _) =>
        prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
    }
  }

  class DelayedGenericTypedJsonSourceFactory(formatterFactory: RecordFormatterFactory,
                                             processObjectDependencies: ProcessObjectDependencies,
                                             timestampAssigner: Option[TimestampWatermarkHandler[TypedJson]])
    extends KafkaSourceFactory[String, TypedMap](
      new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization),
      timestampAssigner,
      formatterFactory,
      processObjectDependencies
    ) with BaseKafkaDelayedSourceFactory {

    override protected def prepareInitialParameters: List[Parameter] = super.prepareInitialParameters ++ List(
      TypeParameter, TimestampFieldParameter, DelayParameter
    )

    override def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
      case step@TransformationStep(
      (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (TypeDefinitionParamName, DefinedEagerParameter(definition: TypeDefinition, _)) ::
        (TimestampFieldParamName, DefinedEagerParameter(field, _)) ::
        (DelayParameterName, DefinedEagerParameter(delay, _)) :: Nil, _
      ) =>
        val topicValidationErrors = topicsValidationErrors(topic)
        calculateTypingResult(definition) match {
          case Valid((definition, typingResult)) =>
            val delayValidationErrors = Option(delay.asInstanceOf[java.lang.Long]).map(d => validateDelay(d)).getOrElse(Nil)
            val timestampValidationErrors = Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, typingResult)).getOrElse(Nil)
            val errors = topicValidationErrors ++ timestampValidationErrors ++ delayValidationErrors
            prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, typingResult, errors)
          case Invalid(exc) =>
            val errors = topicValidationErrors ++ List(exc.toCustomNodeError(nodeId))
            prepareSourceFinalErrors(context, dependencies, step.parameters, errors = errors)
        }
      case step@TransformationStep((TopicParamName, _) :: (TypeDefinitionParamName, _) :: (TimestampFieldParamName, _) :: (DelayParameterName, _) :: Nil, _) =>
        prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
    }

    override protected def createSource(params: Map[String, Any],
                                        dependencies: List[NodeDependencyValue],
                                        finalState: Option[State],
                                        preparedTopics: List[PreparedKafkaTopic],
                                        kafkaConfig: KafkaConfig,
                                        deserializationSchema: KafkaDeserializationSchema[TypedJson],
                                        timestampAssigner: Option[TimestampWatermarkHandler[TypedJson]],
                                        formatter: RecordFormatter,
                                        flinkContextInitializer: FlinkContextInitializer[TypedJson]): FlinkSource[TypedJson] = {
      extractDelayInMillis(params) match {
        case millis if millis > 0 =>
          val timestampFieldName = extractTimestampField(params)
          val timestampAssignerWithExtract: Option[TimestampWatermarkHandler[TypedJson]] =
            Option(timestampFieldName)
              .map(fieldName => {
                prepareTimestampAssigner(
                  kafkaConfig,
                  extractTimestampFromField(fieldName)
                )
              }).orElse(timestampAssigner)
          createDelayedKafkaSourceWithFixedDelay[String, TypedMap](preparedTopics, kafkaConfig, deserializationSchema, timestampAssignerWithExtract, formatter, flinkContextInitializer, millis)
        case _ =>
          super.createSource(params, dependencies, finalState, preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer)
      }
    }

    def extractTimestampFromField(fieldName: String): SerializableTimestampAssigner[TypedJson] = new SerializableTimestampAssigner[TypedJson] {

      override def extractTimestamp(element: TypedJson, recordTimestamp: Long): Long = {
        // TODO: Handle exceptions thrown within sources (now the whole process fails)
        Option(element.value().get(fieldName))
          .map(_.asInstanceOf[Long])
          .getOrElse(0L) // explicit null to 0L conversion (instead of implicit unboxing)
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
      override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = formatter
    }
  }

}
