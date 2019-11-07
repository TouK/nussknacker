package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.internals.KafkaDeserializationSchemaWrapper
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
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
class KafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                             schemaFactory: DeserializationSchemaFactory[T],
                                             timestampAssigner: Option[TimestampAssigner[T]],
                                             testPrepareInfo: TestDataSplit)
  extends BaseKafkaSourceFactory(config, timestampAssigner, testPrepareInfo) {

  def this(config: KafkaConfig,
           schema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampAssigner[T]],
           testPrepareInfo: TestDataSplit) =
    this(config, FixedDeserializationSchemaFactory(new KafkaDeserializationSchemaWrapper(schema)), timestampAssigner, testPrepareInfo)

  @MethodToInvoke
  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Source[T] with TestDataGenerator = {
    createSource(processMetaData, List(topic), schemaFactory.create(List(topic), config))
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

}


class SingleTopicKafkaSourceFactory[T: TypeInformation](topic: String,
                                                        config: KafkaConfig,
                                                        schemaFactory: DeserializationSchemaFactory[T],
                                                        timestampAssigner: Option[TimestampAssigner[T]],
                                                        testPrepareInfo: TestDataSplit)
  extends BaseKafkaSourceFactory(config, timestampAssigner, testPrepareInfo) {

  def this(topic: String,
           config: KafkaConfig,
           schema: DeserializationSchema[T],
           timestampAssigner: Option[TimestampAssigner[T]],
           testPrepareInfo: TestDataSplit) =
    this(topic, config, FixedDeserializationSchemaFactory(new KafkaDeserializationSchemaWrapper(schema)), timestampAssigner, testPrepareInfo)

  @MethodToInvoke
  def create(processMetaData: MetaData): Source[T] with TestDataGenerator = {
    createSource(processMetaData, List(topic), schemaFactory.create(List(topic), config))
  }

}

abstract class BaseKafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                                          val timestampAssigner: Option[TimestampAssigner[T]],
                                                          protected val testPrepareInfo: TestDataSplit)
  extends FlinkSourceFactory[T] with Serializable {

  protected def createSource(processMetaData: MetaData, topics: List[String],
                             schema: KafkaDeserializationSchema[T]): KafkaSource = {
    new KafkaSource(consumerGroupId = processMetaData.id, topics = topics, schema, None)
  }

  class KafkaSource(consumerGroupId: String, topics: List[String], schema: KafkaDeserializationSchema[T], recordFormatterOpt: Option[RecordFormatter])
    extends FlinkSource[T] with Serializable with TestDataParserProvider[T] with TestDataGenerator {

    override val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

    override def toFlinkSource: SourceFunction[T] = {
      topics.foreach(KafkaEspUtils.setToLatestOffsetIfNeeded(config, _, consumerGroupId))
      createFlinkSource()
    }

    protected def createFlinkSource(): FlinkKafkaConsumer[T] = {
      new FlinkKafkaConsumer[T](topics.asJava, schema, KafkaEspUtils.toProperties(config, Some(consumerGroupId)))
    }

    override def generateTestData(size: Int): Array[Byte] = {
      val listsFromAllTopics = topics.map(KafkaEspUtils.readLastMessages(_, size, config))
      val merged = ListUtil.mergeListsFromTopics(listsFromAllTopics, size)
      val formatted = recordFormatterOpt.map(formatter => merged.map(formatter.formatRecord)).getOrElse {
        merged.map(_.value())
      }
      testPrepareInfo.joinData(formatted)
    }

    override def testDataParser: TestDataParser[T] = new TestDataParser[T] {
      override def parseTestData(merged: Array[Byte]): List[T] =
        testPrepareInfo.splitData(merged).map { formatted =>
          val topic = topics.head
          val record = recordFormatterOpt
            .map(formatter => formatter.parseRecord(formatted))
            .getOrElse(new ProducerRecord(topic, formatted))
          schema.deserialize(new ConsumerRecord[Array[Byte], Array[Byte]](topic, -1, -1, record.key(), record.value()))
        }
    }

    override def timestampAssigner: Option[TimestampAssigner[T]] = BaseKafkaSourceFactory.this.timestampAssigner
  }

}