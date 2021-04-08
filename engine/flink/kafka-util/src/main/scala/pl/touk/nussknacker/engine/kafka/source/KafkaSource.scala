package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.process.{TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka._

import scala.collection.JavaConverters._

class KafkaSource[T](preparedTopics: List[PreparedKafkaTopic],
                     kafkaConfig: KafkaConfig,
                     deserializationSchema: KafkaDeserializationSchema[T],
                     timestampAssigner: Option[TimestampWatermarkHandler[T]],
                     recordFormatter: RecordFormatter,
                     overriddenConsumerGroup: Option[String] = None)
  extends FlinkSource[T]
    with Serializable
    with TestDataParserProvider[T]
    with TestDataGenerator with ExplicitUidInOperatorsSupport {

  private lazy val topics: List[String] = preparedTopics.map(_.prepared)

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[T] = {
    val consumerGroupId = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(flinkNodeContext))
    env.setStreamTimeCharacteristic(if (timestampAssigner.isDefined) TimeCharacteristic.EventTime else TimeCharacteristic.IngestionTime)

    val newStart = setUidToNodeIdIfNeed(flinkNodeContext,
      env
        .addSource[T](flinkSourceFunction(consumerGroupId))(typeInformation)
        .name(s"${flinkNodeContext.metaData.id}-${flinkNodeContext.nodeId}-source"))

    timestampAssigner.map(_.assignTimestampAndWatermarks(newStart)).getOrElse(newStart)
  }

  override val typeInformation: TypeInformation[T] = deserializationSchema.getProducedType

  protected def flinkSourceFunction(consumerGroupId: String): SourceFunction[T] = {
    topics.foreach(KafkaUtils.setToLatestOffsetIfNeeded(kafkaConfig, _, consumerGroupId))
    createFlinkSource(consumerGroupId)
  }

  protected def createFlinkSource(consumerGroupId: String): FlinkKafkaConsumer[T] = {
    new FlinkKafkaConsumer[T](topics.asJava, deserializationSchema, KafkaUtils.toProperties(kafkaConfig, Some(consumerGroupId)))
  }

  override def generateTestData(size: Int): Array[Byte] = {
    val listsFromAllTopics = topics.map(KafkaUtils.readLastMessages(_, size, kafkaConfig))
    val merged = ListUtil.mergeListsFromTopics(listsFromAllTopics, size)
    recordFormatter.prepareGeneratedTestData(merged)
  }

  override def testDataParser: TestDataParser[T] = new TestDataParser[T] {
    override def parseTestData(merged: Array[Byte]): List[T] = {
      val topic = topics.head
      recordFormatter.parseDataForTest(topic, merged).map {deserializeTestData(topic, _)}
    }
  }

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]] = timestampAssigner

  //There is deserializationSchema.deserialize method which doesn't need Collector, however
  //for some reason KafkaDeserializationSchemaWrapper throws Exception when used in such way...
  //protected to make it easier for backward compatibility
  protected def deserializeTestData(topic: String, record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    val collector = new SimpleCollector
    deserializationSchema.deserialize(record, collector)
    collector.output
  }

  private class SimpleCollector extends Collector[T] {
    var output: T = _
    override def collect(record: T): Unit = {
      output = record
    }
    override def close(): Unit = {}
  }

}
