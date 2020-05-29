package pl.touk.nussknacker.engine.kafka.source

import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestDataSplit
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaDeserializationSchemaFactory, KafkaDeserializationSchemaFactory}

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
class KafkaSourceFactory[T: TypeInformation](deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                             timestampAssigner: Option[TimestampAssigner[T]],
                                             testPrepareInfo: TestDataSplit,
                                             processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory(deserializationSchemaFactory, timestampAssigner, testPrepareInfo, processObjectDependencies) {

  def this(deserializationSchema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampAssigner[T]],
           testPrepareInfo: TestDataSplit,
           processObjectDependencies: ProcessObjectDependencies) =
    this(
      FixedKafkaDeserializationSchemaFactory(deserializationSchema),
      timestampAssigner,
      testPrepareInfo,
      processObjectDependencies
    )

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String)
            (implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    createSource(List(topic), kafkaConfig, processMetaData, nodeId)
  }
}

class SingleTopicKafkaSourceFactory[T: TypeInformation](topic: String,
                                                        deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                                        timestampAssigner: Option[TimestampAssigner[T]],
                                                        testPrepareInfo: TestDataSplit,
                                                        processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory(deserializationSchemaFactory, timestampAssigner, testPrepareInfo, processObjectDependencies) {

  def this(topic: String,
           deserializationSchema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampAssigner[T]],
           testPrepareInfo: TestDataSplit,
           processObjectDependencies: ProcessObjectDependencies) =
    this(
      topic,
      FixedKafkaDeserializationSchemaFactory(deserializationSchema),
      timestampAssigner,
      testPrepareInfo,
      processObjectDependencies
    )

  @MethodToInvoke
  def create(processMetaData: MetaData)(implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    createSource(List(topic), kafkaConfig, processMetaData, nodeId)
  }
}

abstract class BaseKafkaSourceFactory[T: TypeInformation](deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                                          timestampAssigner: Option[TimestampAssigner[T]],
                                                          testPrepareInfo: TestDataSplit,
                                                          processObjectDependencies: ProcessObjectDependencies)
  extends FlinkSourceFactory[T] with Serializable {

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  protected def createSource(topics: List[String], kafkaConfig: KafkaConfig, processMetaData: MetaData, nodeId: NodeId): KafkaSource[T] =
    createSource(topics, kafkaConfig)

  protected def createSource(topics: List[String], kafkaConfig: KafkaConfig): KafkaSource[T] = {
    val serializationSchema = deserializationSchemaFactory.create(topics, kafkaConfig)
    new KafkaSource(topics = topics, kafkaConfig, serializationSchema, timestampAssigner, None, testPrepareInfo, processObjectDependencies)
  }
}
