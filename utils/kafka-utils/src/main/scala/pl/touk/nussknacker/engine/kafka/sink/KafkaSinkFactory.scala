package pl.touk.nussknacker.engine.kafka.sink

import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaSerializationSchemaFactory, KafkaSerializationSchema, KafkaSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PreparedKafkaTopic, serialization}

import javax.validation.constraints.NotBlank

class KafkaSinkFactory(serializationSchemaFactory: KafkaSerializationSchemaFactory[AnyRef],
                       processObjectDependencies: ProcessObjectDependencies,
                       implProvider: KafkaSinkImplFactory)
  extends BaseKafkaSinkFactory(serializationSchemaFactory, processObjectDependencies, implProvider) {

  def this(serializationSchema: String => serialization.KafkaSerializationSchema[AnyRef],
           processObjectDependencies: ProcessObjectDependencies,
           implProvider: KafkaSinkImplFactory) =
    this(FixedKafkaSerializationSchemaFactory(serializationSchema), processObjectDependencies, implProvider)

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

abstract class BaseKafkaSinkFactory(serializationSchemaFactory: KafkaSerializationSchemaFactory[AnyRef],
                                    processObjectDependencies: ProcessObjectDependencies,
                                    implProvider: KafkaSinkImplFactory)
  extends SinkFactory {

  protected def createSink(topic: String, value: LazyParameter[AnyRef], processMetaData: MetaData): Sink = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val preparedTopic = KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)
    KafkaUtils.validateTopicsExistence(List(preparedTopic), kafkaConfig)
    val serializationSchema = serializationSchemaFactory.create(preparedTopic.prepared, kafkaConfig)
    val clientId = s"${processMetaData.id}-${preparedTopic.prepared}"
    implProvider.prepareSink(preparedTopic, value, kafkaConfig, serializationSchema, clientId)
  }

}

trait KafkaSinkImplFactory {

  // TODO: handle key passed by user - not only extracted by serialization schema from value
  def prepareSink(topic: PreparedKafkaTopic, value: LazyParameter[AnyRef], kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[AnyRef], clientId: String): Sink

}