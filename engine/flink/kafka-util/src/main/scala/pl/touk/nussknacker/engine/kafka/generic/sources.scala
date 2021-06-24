package pl.touk.nussknacker.engine.kafka.generic

import io.circe.{Decoder, Json, JsonObject}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, MandatoryParameterValidator, NotBlankParameterValidator, Parameter}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.{BasicRecordFormatter, KafkaConfig, RecordFormatter, RecordFormatterFactory}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import java.nio.charset.StandardCharsets
import java.util
import java.util.Collections
import scala.reflect.ClassTag

//TODO: Move it to source package
object sources {

  import collection.JavaConverters._

  class GenericJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, java.util.Map[_, _]](
    new FixedValueDeserializationSchemaFactory(JsonMapDeserialization), None, FixedRecordFormatterFactoryWrapper(JsonRecordFormatter), processObjectDependencies)

  class GenericTypedJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, TypedMap](
    new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization), None, FixedRecordFormatterFactoryWrapper(JsonRecordFormatter), processObjectDependencies) {

    protected val TypeDefinitionParamName = "type"

    protected val TypeParameter: Parameter = Parameter[java.util.Map[String, _]](TypeDefinitionParamName).copy(
      validators = List(MandatoryParameterValidator, NotBlankParameterValidator),
      editor = Some(JsonParameterEditor)
    )

    override protected def prepareInitialParameters: List[Parameter] = super.prepareInitialParameters ++ List(TypeParameter)

    override protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
      case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (TypeDefinitionParamName, DefinedEagerParameter(definition: Any, _)) :: Nil, _) =>
        val typingResult = TypingUtils.typeMapDefinition(definition.asInstanceOf[java.util.Map[String, _]])
        prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, typingResult, Nil)
      case step@TransformationStep((TopicParamName, top) :: (TypeDefinitionParamName, typ) :: Nil, _) =>
        prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
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
