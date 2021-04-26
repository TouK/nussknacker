package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaDeserializationSchemaFactory, KafkaDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, RecordFormatter}

import javax.validation.constraints.NotBlank
import scala.reflect.ClassTag

/** <pre>
  * Wrapper for [[org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer]]
  * Features:
  *   - fetch latest N records which can be later used to test process in UI
  * Fetching data is defined in [[pl.touk.nussknacker.engine.kafka.source.KafkaSource]] which
  * extends [[pl.touk.nussknacker.engine.api.process.TestDataGenerator]]. See [[pl.touk.nussknacker.engine.kafka.KafkaUtils#readLastMessages]]
  *   - reset Kafka's offset to latest value - `forceLatestRead` property, see [[pl.touk.nussknacker.engine.kafka.KafkaUtils#setOffsetToLatest]]
  *
  * BaseKafkaSourceFactory comes in two variants:
  *   - KafkaSourceFactory - `topic` parameter has to be passed on frontend
  *   - SingleTopicKafkaSourceFactory - topic is defined on level of configuration
  *
  * </pre>
  * */
class KafkaSourceFactory[T: ClassTag](deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                      timestampAssigner: Option[TimestampWatermarkHandler[T]],
                                      formatter: RecordFormatter,
                                      processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory(deserializationSchemaFactory, timestampAssigner, formatter, processObjectDependencies) {

  def this(deserializationSchema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampWatermarkHandler[T]],
           formatter: RecordFormatter,
           processObjectDependencies: ProcessObjectDependencies) =
    this(
      FixedKafkaDeserializationSchemaFactory(deserializationSchema),
      timestampAssigner,
      formatter,
      processObjectDependencies
    )

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String)
            (implicit nodeId: NodeId): KafkaSource[T] = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    createSource(List(topic), kafkaConfig, processMetaData, nodeId)
  }
}

class SingleTopicKafkaSourceFactory[T: ClassTag](topic: String,
                                                 deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                                 timestampAssigner: Option[TimestampWatermarkHandler[T]],
                                                 formatter: RecordFormatter,
                                                 processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory(deserializationSchemaFactory, timestampAssigner, formatter, processObjectDependencies) {

  def this(topic: String,
           deserializationSchema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampWatermarkHandler[T]],
           formatter: RecordFormatter,
           processObjectDependencies: ProcessObjectDependencies) =
    this(
      topic,
      FixedKafkaDeserializationSchemaFactory(deserializationSchema),
      timestampAssigner,
      formatter,
      processObjectDependencies
    )

  @MethodToInvoke
  def create(processMetaData: MetaData)(implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    createSource(List(topic), kafkaConfig, processMetaData, nodeId)
  }
}

abstract class BaseKafkaSourceFactory[T: ClassTag](deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                                   timestampAssigner: Option[TimestampWatermarkHandler[T]],
                                                   formatter: RecordFormatter,
                                                   processObjectDependencies: ProcessObjectDependencies)
  extends FlinkSourceFactory[T] with Serializable {

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  protected def createSource(topics: List[String], kafkaConfig: KafkaConfig, processMetaData: MetaData, nodeId: NodeId): KafkaSource[T] =
    createSource(topics, kafkaConfig)

  protected def createSource(topics: List[String], kafkaConfig: KafkaConfig): KafkaSource[T] = {
    val preparedTopics = topics.map(KafkaUtils.prepareKafkaTopic(_, processObjectDependencies))
    KafkaUtils.validateTopicsExistence(preparedTopics, kafkaConfig)
    val serializationSchema = deserializationSchemaFactory.create(topics, kafkaConfig)
    new KafkaSource(preparedTopics, kafkaConfig, serializationSchema, timestampAssigner, formatter)
  }
}
