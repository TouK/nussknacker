package pl.touk.nussknacker.engine.kafka.sink

import javax.validation.constraints.NotBlank
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaSerializationSchemaFactory, KafkaSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

/** <pre>
  * Wrapper for [[org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer]]
  *
  * Producing data is defined in [[pl.touk.nussknacker.engine.kafka.sink.KafkaSink]]

  * </pre>
  * */
class KafkaSinkFactory(serializationSchemaFactory: KafkaSerializationSchemaFactory[Any], processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSinkFactory(serializationSchemaFactory, processObjectDependencies) {

  def this(serializationSchema: String => KafkaSerializationSchema[Any], processObjectDependencies: ProcessObjectDependencies) =
    this(FixedKafkaSerializationSchemaFactory(serializationSchema), processObjectDependencies)

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

abstract class BaseKafkaSinkFactory(serializationSchemaFactory: KafkaSerializationSchemaFactory[Any], processObjectDependencies: ProcessObjectDependencies)
  extends SinkFactory {

  protected def createSink(topic: String, processMetaData: MetaData): KafkaSink = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val preparedTopic = KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)
    KafkaUtils.validateTopicsExistence(List(preparedTopic), kafkaConfig)
    val serializationSchema = serializationSchemaFactory.create(preparedTopic.prepared, kafkaConfig)
    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    new KafkaSink(topic, kafkaConfig, serializationSchema, clientId)
  }
}
