package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkIntermediateRawSource, FlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka._

import scala.collection.JavaConverters._

class KafkaSource[T](preparedTopics: List[PreparedKafkaTopic],
                     kafkaConfig: KafkaConfig,
                     deserializationSchema: KafkaDeserializationSchema[T],
                     override val timestampAssigner: Option[TimestampWatermarkHandler[T]],
                     recordFormatter: RecordFormatter,
                     overriddenConsumerGroup: Option[String] = None)
  extends FlinkSource[T]
    with FlinkIntermediateRawSource[T]
    with Serializable
    with FlinkSourceTestSupport[T]
    with TestDataGenerator
    with ExplicitUidInOperatorsSupport {

  private lazy val topics: List[String] = preparedTopics.map(_.prepared)

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    val consumerGroupId = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(flinkNodeContext))
    val sourceFunction = flinkSourceFunction(consumerGroupId)

    prepareSourceStream(env, flinkNodeContext, sourceFunction)
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

  protected def deserializeTestData(topic: String, record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    // we use deserialize(record) instead of deserialize(record, collector) for backward compatibility reasons
    deserializationSchema.deserialize(record)
  }

}
