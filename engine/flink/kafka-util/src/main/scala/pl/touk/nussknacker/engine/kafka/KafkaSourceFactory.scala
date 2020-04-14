package pl.touk.nussknacker.engine.kafka

import javax.validation.constraints.NotBlank
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.internals.KafkaDeserializationSchemaWrapper
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSource, FlinkSourceFactory}
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
             topic: String): Source[T] with TestDataGenerator = {
    createSource(processMetaData, List(topic), schemaFactory.create(List(topic), kafkaConfig))
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

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
  def create(processMetaData: MetaData): Source[T] with TestDataGenerator = {
    createSource(processMetaData, List(topic), schemaFactory.create(List(topic), kafkaConfig))
  }

}

abstract class BaseKafkaSourceFactory[T: TypeInformation](val timestampAssigner: Option[TimestampAssigner[T]],
                                                          protected val testPrepareInfo: TestDataSplit,
                                                          processObjectDependencies: ProcessObjectDependencies)
  extends FlinkSourceFactory[T] with Serializable {

  protected def createSource(processMetaData: MetaData, topics: List[String],
                             schema: KafkaDeserializationSchema[T]): KafkaSource = {
    new KafkaSource(consumerGroupId = processMetaData.id, topics = topics, schema, None, processObjectDependencies)
  }

  val kafkaConfig: KafkaConfig = processObjectDependencies.config.as[KafkaConfig]("kafka")

  class KafkaSource(consumerGroupId: String,
                    topics: List[String],
                    schema: KafkaDeserializationSchema[T],
                    recordFormatterOpt: Option[RecordFormatter],
                    processObjectDependencies: ProcessObjectDependencies)
      extends BasicFlinkSource[T]
        with Serializable
        with TestDataParserProvider[T]
        with TestDataGenerator {


    def preparedTopics: List[String] = topics.map(processObjectDependencies
                                                    .objectNaming
                                                    .prepareName(_,
                                                      processObjectDependencies.config,
                                                      new NamingContext(KafkaUsageKey)))

    override val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

    override def flinkSourceFunction: SourceFunction[T] = {
      preparedTopics.foreach(KafkaEspUtils.setToLatestOffsetIfNeeded(kafkaConfig, _, consumerGroupId))
      createFlinkSource()
    }

    protected def createFlinkSource(): FlinkKafkaConsumer[T] = {
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

    override def timestampAssigner: Option[TimestampAssigner[T]] = BaseKafkaSourceFactory.this.timestampAssigner
  }

}
