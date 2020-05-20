package pl.touk.nussknacker.engine.kafka

import java.nio.charset.StandardCharsets

import javax.validation.constraints.NotBlank
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.engine.kafka.serialization.{FixedSerializationSchemaFactory, SerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.BaseKafkaSinkFactory._

class KafkaSinkFactory(serializationSchemaFactory: SerializationSchemaFactory[Any], processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSinkFactory(serializationSchemaFactory, processObjectDependencies) {

  def this(schema: String => KafkaSerializationSchema[Any], processObjectDependencies: ProcessObjectDependencies) =
    this(FixedSerializationSchemaFactory(schema), processObjectDependencies)

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String
            ): Sink =
    createSink(topic, processMetaData)
}

object BaseKafkaSinkFactory {
  final val TopicParamName = "topic"
}

abstract class BaseKafkaSinkFactory(serializationSchemaFactory: SerializationSchemaFactory[Any], processObjectDependencies: ProcessObjectDependencies)
  extends SinkFactory {

  protected def createSink(topic: String, processMetaData: MetaData): KafkaSink = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val preparedTopic = KafkaUtils.prepareTopicName(topic, processObjectDependencies)
    val serializationSchema = serializationSchemaFactory.create(preparedTopic, kafkaConfig)
    val clientId = s"${processMetaData.id}-$preparedTopic"
    new KafkaSink(topic, kafkaConfig, serializationSchema, clientId)
  }

  class KafkaSink(topic: String, kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[Any], clientId: String)
    extends BasicFlinkSink with Serializable {

    override def toFlinkFunction: SinkFunction[Any] =
      PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)

    override def testDataOutput: Option[Any => String] = Option(value =>
      new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8)
    )
  }
}
