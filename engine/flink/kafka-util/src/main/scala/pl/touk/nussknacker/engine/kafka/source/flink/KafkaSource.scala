package pl.touk.nussknacker.engine.kafka.source.flink

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process.TestDataGenerator
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkCustomNodeContext, FlinkIntermediateRawSource, FlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler.SimpleSerializableTimestampAssigner
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.flink.KafkaFlinkDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSource.defaultMaxOutOfOrdernessMillis

import java.time.Duration
import scala.annotation.nowarn
import scala.collection.JavaConverters._
import scala.reflect.classTag

class KafkaSource[T](preparedTopics: List[PreparedKafkaTopic],
                     kafkaConfig: KafkaConfig,
                     deserializationSchema: KafkaFlinkDeserializationSchema[T],
                     passedAssigner: Option[TimestampWatermarkHandler[T]],
                     recordFormatter: RecordFormatter,
                     overriddenConsumerGroup: Option[String] = None)
  extends FlinkSource[T]
    with FlinkIntermediateRawSource[T]
    with Serializable
    with FlinkSourceTestSupport[T]
    with TestDataGenerator
    with ExplicitUidInOperatorsSupport {

  private lazy val topics: List[String] = preparedTopics.map(_.prepared)

  def wrapToFlinkDeserializationSchema(deserializationSchema: KafkaFlinkDeserializationSchema[T]): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {

      override def getProducedType: TypeInformation[T] = {
        val clazz = classTag.runtimeClass.asInstanceOf[Class[T]]
        TypeInformation.of(clazz)
      }

      override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = deserializationSchema.deserialize(record)
    }
  }

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    val consumerGroupId = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(flinkNodeContext))
    val sourceFunction = flinkSourceFunction(consumerGroupId)

    prepareSourceStream(env, flinkNodeContext, sourceFunction)
  }

  override val typeInformation: TypeInformation[T] = {
    val clazz = classTag.runtimeClass.asInstanceOf[Class[T]]
    TypeInformation.of(clazz)
  }

  protected def flinkSourceFunction(consumerGroupId: String): SourceFunction[T] = {
    topics.foreach(KafkaUtils.setToLatestOffsetIfNeeded(kafkaConfig, _, consumerGroupId))
    createFlinkSource(consumerGroupId)
  }

  @silent("deprecated")
  @nowarn("cat=deprecation")
  protected def createFlinkSource(consumerGroupId: String): SourceFunction[T] = {
    new FlinkKafkaConsumer[T](topics.asJava, wrapToFlinkDeserializationSchema(deserializationSchema), KafkaUtils.toProperties(kafkaConfig, Some(consumerGroupId)))
  }

  override def generateTestData(size: Int): Array[Byte] = {
    val listsFromAllTopics = topics.map(KafkaUtils.readLastMessages(_, size, kafkaConfig))
    val merged = ListUtil.mergeListsFromTopics(listsFromAllTopics, size)
    recordFormatter.prepareGeneratedTestData(merged)
  }

  override def testDataParser: TestDataParser[T] = new TestDataParser[T] {
    override def parseTestData(merged: TestData): List[T] = {
      val topic = topics.head
      recordFormatter.parseDataForTest(topic, merged.testData).map {
        deserializeTestData(topic, _)
      }
    }
  }

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]] = timestampAssigner

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = Some(
    passedAssigner.getOrElse(new StandardTimestampWatermarkHandler[T](WatermarkStrategy
      .forBoundedOutOfOrderness(Duration.ofMillis(kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)))))
  )

  protected def deserializeTestData(topic: String, record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    // we use deserialize(record) instead of deserialize(record, collector) for backward compatibility reasons
    wrapToFlinkDeserializationSchema(deserializationSchema).deserialize(record)
  }

}

class ConsumerRecordBasedKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic],
                                           kafkaConfig: KafkaConfig,
                                           deserializationSchema: KafkaFlinkDeserializationSchema[ConsumerRecord[K, V]],
                                           timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                           formatter: RecordFormatter,
                                           override val contextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]]) extends KafkaSource[ConsumerRecord[K, V]](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter) {

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]] = timestampAssigner.orElse(Some(
    StandardTimestampWatermarkHandler.afterEachEvent[ConsumerRecord[K, V]]((_.timestamp()): SimpleSerializableTimestampAssigner[ConsumerRecord[K, V]])
  ))

  override val typeInformation: TypeInformation[ConsumerRecord[K, V]] = {
    val clazz = classTag[ConsumerRecord[K, V]].runtimeClass.asInstanceOf[Class[ConsumerRecord[K, V]]]
    TypeInformation.of(clazz)
  }
}

object KafkaSource {
  val defaultMaxOutOfOrdernessMillis = 60000
}
