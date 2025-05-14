package pl.touk.nussknacker.engine.kafka.sink

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, TopicName}
import pl.touk.nussknacker.engine.kafka.{
  serialization,
  KafkaComponentsUtils,
  KafkaConfig,
  KafkaUtils,
  PreparedKafkaTopic
}
import pl.touk.nussknacker.engine.kafka.serialization.{
  FixedKafkaSerializationSchemaFactory,
  KafkaSerializationSchema,
  KafkaSerializationSchemaFactory
}
import pl.touk.nussknacker.engine.kafka.validator.CachedTopicsExistenceValidator

import javax.validation.constraints.NotBlank

class KafkaSinkFactory(
    serializationSchemaFactory: KafkaSerializationSchemaFactory[AnyRef],
    modelConfig: ModelConfig,
    implProvider: KafkaSinkImplFactory
) extends BaseKafkaSinkFactory(serializationSchemaFactory, modelConfig, implProvider) {

  def this(
      serializationSchema: String => serialization.KafkaSerializationSchema[AnyRef],
      modelConfig: ModelConfig,
      implProvider: KafkaSinkImplFactory
  ) =
    this(FixedKafkaSerializationSchemaFactory(serializationSchema), modelConfig, implProvider)

  @MethodToInvoke
  def create(
      processMetaData: MetaData,
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      @ParamName("Topic") @NotBlank topic: String,
      @ParamName("Value") value: LazyParameter[AnyRef]
  ): Sink =
    createSink(TopicName.ForSink(topic), value, processMetaData)

}

abstract class BaseKafkaSinkFactory(
    serializationSchemaFactory: KafkaSerializationSchemaFactory[AnyRef],
    modelConfig: ModelConfig,
    implProvider: KafkaSinkImplFactory
) extends SinkFactory {

  @transient private lazy val kafkaConfig          = KafkaConfig.parseConfig(modelConfig.underlyingConfig)
  @transient private lazy val lazyKafkaAdminClient = KafkaUtils.createLazyKafkaAdminClient(kafkaConfig)

  protected def createSink(topic: TopicName.ForSink, value: LazyParameter[AnyRef], processMetaData: MetaData): Sink = {
    val preparedTopic = KafkaComponentsUtils.prepareKafkaTopic(topic, modelConfig)
    new CachedTopicsExistenceValidator(kafkaConfig.topicsExistenceValidationConfig, lazyKafkaAdminClient)
      .validateTopics(NonEmptyList.one(preparedTopic).map(_.prepared))
      .valueOr(err => throw err)
    val serializationSchema = serializationSchemaFactory.create(preparedTopic.prepared, kafkaConfig)
    val clientId            = s"${processMetaData.name}-${preparedTopic.prepared}"
    implProvider.prepareSink(preparedTopic, value, kafkaConfig, serializationSchema, clientId)
  }

}

trait KafkaSinkImplFactory {

  // TODO: handle key passed by user - not only extracted by serialization schema from value
  def prepareSink(
      topic: PreparedKafkaTopic[TopicName.ForSink],
      value: LazyParameter[AnyRef],
      kafkaConfig: KafkaConfig,
      serializationSchema: KafkaSerializationSchema[AnyRef],
      clientId: String
  ): Sink

}
