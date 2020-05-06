package pl.touk.nussknacker.engine.kafka

import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.internals.KafkaDeserializationSchemaWrapper
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, FixedDeserializationSchemaFactory}

import scala.collection.JavaConverters._

/** <pre>
  * Wrapper for [[org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer]]
  * Features:
  *   - fetch latest N records which can be later used to test process in UI
  * Fetching data is defined in [[pl.touk.nussknacker.engine.kafka.BaseKafkaSourceFactory.KafkaSource]] which
  * extends [[pl.touk.nussknacker.engine.api.process.TestDataGenerator]]. See [[pl.touk.nussknacker.engine.kafka.KafkaEspUtils#readLastMessages]]
  *   - reset Kafka's offset to latest value - `forceLatestRead` property, see [[pl.touk.nussknacker.engine.kafka.KafkaEspUtils#setOffsetToLatest]]
  *
  * BaseKafkaSourceFactory comes in two variants:
  *   - KafkaSourceFactory - `topic` parameter has to be passed on frontend
  *   - SingleTopicKafkaSourceFactory - topic is defined on level of configuration
  *
  * </pre>
  * */
class KafkaSourceFactory[T: TypeInformation](schemaFactory: DeserializationSchemaFactory[T],
                                             timestampAssigner: Option[TimestampAssigner[T]],
                                             testPrepareInfo: TestDataSplit,
                                             processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory(timestampAssigner, testPrepareInfo, processObjectDependencies) {

  def this(schema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampAssigner[T]],
           testPrepareInfo: TestDataSplit,
           processObjectDependencies: ProcessObjectDependencies) =
    this(FixedDeserializationSchemaFactory(new KafkaDeserializationSchemaWrapper(schema)),
                                                    timestampAssigner,
                                                    testPrepareInfo,
                                                    processObjectDependencies)

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @ParamName(`TopicParamName`)
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @NotBlank
             topic: String)(implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaSourceFactory.parseKafkaConfig(processObjectDependencies)
    createSource(List(topic), kafkaConfig, schemaFactory.create(List(topic), kafkaConfig), processMetaData, nodeId)
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

  def parseKafkaConfig(processObjectDependencies: ProcessObjectDependencies): KafkaConfig =
    KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")

}


class SingleTopicKafkaSourceFactory[T: TypeInformation](topic: String,
                                                        schemaFactory: DeserializationSchemaFactory[T],
                                                        timestampAssigner: Option[TimestampAssigner[T]],
                                                        testPrepareInfo: TestDataSplit,
                                                        processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory(timestampAssigner, testPrepareInfo, processObjectDependencies) {

  def this(topic: String,
           schema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampAssigner[T]],
           testPrepareInfo: TestDataSplit,
           processObjectDependencies: ProcessObjectDependencies) =
    this(topic, FixedDeserializationSchemaFactory(new KafkaDeserializationSchemaWrapper(schema)),
                                                          timestampAssigner,
                                                          testPrepareInfo,
                                                          processObjectDependencies)

  @MethodToInvoke
  def create(processMetaData: MetaData)(implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaSourceFactory.parseKafkaConfig(processObjectDependencies)
    createSource(List(topic), kafkaConfig, schemaFactory.create(List(topic), kafkaConfig), processMetaData, nodeId)
  }

}

abstract class BaseKafkaSourceFactory[T: TypeInformation](val timestampAssigner: Option[TimestampAssigner[T]],
                                                          protected val testPrepareInfo: TestDataSplit,
                                                          processObjectDependencies: ProcessObjectDependencies)
  extends FlinkSourceFactory[T] with Serializable {

  @deprecated("Should be used version without process MetaData", "0.1.1")
  protected def createSource(processMetaData: MetaData, topics: List[String],
                             schema: KafkaDeserializationSchema[T]): KafkaSource = {
    createSource(topics, KafkaSourceFactory.parseKafkaConfig(processObjectDependencies), schema)
  }

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  protected def createSource(topics: List[String], kafkaConfig: KafkaConfig, schema: KafkaDeserializationSchema[T],
                             processMetaData: MetaData, nodeId: NodeId): KafkaSource = {
    createSource(topics, kafkaConfig, schema)
  }

  protected def createSource(topics: List[String], kafkaConfig: KafkaConfig, schema: KafkaDeserializationSchema[T]): KafkaSource = {
    new KafkaSource(topics = topics, kafkaConfig, schema, None, processObjectDependencies)
  }

  class KafkaSource(topics: List[String],
                    kafkaConfig: KafkaConfig,
                    schema: KafkaDeserializationSchema[T],
                    recordFormatterOpt: Option[RecordFormatter],
                    processObjectDependencies: ProcessObjectDependencies,
                    overriddenConsumerGroup: Option[String] = None)
      extends FlinkSource[T]
        with Serializable
        with TestDataParserProvider[T]
        with TestDataGenerator with ExplicitUidInOperatorsSupport {

    override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[T] = {
      val consumerGroupId = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(flinkNodeContext))
      env.setStreamTimeCharacteristic(if (timestampAssigner.isDefined) TimeCharacteristic.EventTime else TimeCharacteristic.IngestionTime)

      val newStart = setUidToNodeIdIfNeed(flinkNodeContext,
        env
          .addSource[T](flinkSourceFunction(consumerGroupId))(typeInformation)
          .name(s"${flinkNodeContext.metaData.id}-${flinkNodeContext.nodeId}-source"))

      timestampAssigner.map {
        case periodic: AssignerWithPeriodicWatermarks[T@unchecked] =>
          newStart.assignTimestampsAndWatermarks(periodic)
        case punctuated: AssignerWithPunctuatedWatermarks[T@unchecked] =>
          newStart.assignTimestampsAndWatermarks(punctuated)
      }.getOrElse(newStart)
    }

    def preparedTopics: List[String] = topics.map(processObjectDependencies
                                                    .objectNaming
                                                    .prepareName(_,
                                                      processObjectDependencies.config,
                                                      new NamingContext(KafkaUsageKey)))

    protected val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

    protected def flinkSourceFunction(consumerGroupId: String): SourceFunction[T] = {
      preparedTopics.foreach(KafkaEspUtils.setToLatestOffsetIfNeeded(kafkaConfig, _, consumerGroupId))
      createFlinkSource(consumerGroupId)
    }

    protected def createFlinkSource(consumerGroupId: String): FlinkKafkaConsumer[T] = {
      new FlinkKafkaConsumer[T](preparedTopics.asJava, schema, KafkaEspUtils.toProperties(kafkaConfig, Some(consumerGroupId)))
    }

    override def generateTestData(size: Int): Array[Byte] = {
      val listsFromAllTopics = preparedTopics.map(KafkaEspUtils.readLastMessages(_, size, kafkaConfig))
      val merged = ListUtil.mergeListsFromTopics(listsFromAllTopics, size)
      val formatted = recordFormatterOpt.map(formatter => merged.map(formatter.formatRecord)).getOrElse {
        merged.map(_.value())
      }
      testPrepareInfo.joinData(formatted)
    }

    override def testDataParser: TestDataParser[T] = new TestDataParser[T] {
      override def parseTestData(merged: Array[Byte]): List[T] =
        testPrepareInfo.splitData(merged).map { formatted =>
          val topic = preparedTopics.head
          val record = recordFormatterOpt
            .map(formatter => formatter.parseRecord(formatted))
            .getOrElse(new ProducerRecord(topic, formatted))
          schema.deserialize(new ConsumerRecord[Array[Byte], Array[Byte]](topic, -1, -1, record.key(), record.value()))
        }
    }

    override def timestampAssignerForTest : Option[TimestampAssigner[T]] = BaseKafkaSourceFactory.this.timestampAssigner
  }

}
