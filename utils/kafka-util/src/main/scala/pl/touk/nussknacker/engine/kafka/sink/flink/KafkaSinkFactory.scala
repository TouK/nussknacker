package pl.touk.nussknacker.engine.kafka.sink.flink

import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaSerializationSchemaFactory, KafkaSerializationSchema, KafkaSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, serialization}

import javax.validation.constraints.NotBlank

// TODO: move to non-flink package
abstract class KafkaSinkFactory(serializationSchemaFactory: KafkaSerializationSchemaFactory[AnyRef], processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSinkFactory(serializationSchemaFactory, processObjectDependencies) {

  def this(serializationSchema: String => serialization.KafkaSerializationSchema[AnyRef], processObjectDependencies: ProcessObjectDependencies) =
    this(FixedKafkaSerializationSchemaFactory(serializationSchema), processObjectDependencies)

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String,
             @ParamName("value") value: LazyParameter[AnyRef]): Sink =
    createSink(topic, value, processMetaData)
}

abstract class BaseKafkaSinkFactory(serializationSchemaFactory: KafkaSerializationSchemaFactory[AnyRef], processObjectDependencies: ProcessObjectDependencies)
  extends SinkFactory {

  protected def createSink(topic: String, value: LazyParameter[AnyRef], processMetaData: MetaData): Sink = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val preparedTopic = KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)
    KafkaUtils.validateTopicsExistence(List(preparedTopic), kafkaConfig)
    val serializationSchema = serializationSchemaFactory.create(preparedTopic.prepared, kafkaConfig)
    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    prepareKafkaComponentImpl(topic, value, kafkaConfig, serializationSchema, clientId)
  }

  protected def prepareKafkaComponentImpl(topic: String, value: LazyParameter[AnyRef], kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[AnyRef], clientId: String): Sink

}
