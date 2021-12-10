package pl.touk.nussknacker.engine.kafka.source.flink

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkIntermediateRawSource, FlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler.SimpleSerializableTimestampAssigner
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.wrapToFlinkDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.defaultMaxOutOfOrdernessMillis

import java.time.Duration
import scala.annotation.nowarn
import scala.collection.JavaConverters._

class FlinkKafkaSource[T](preparedTopics: List[PreparedKafkaTopic],
                          val kafkaConfig: KafkaConfig,
                          deserializationSchema: serialization.KafkaDeserializationSchema[T],
                          passedAssigner: Option[TimestampWatermarkHandler[T]],
                          val formatter: RecordFormatter,
                          overriddenConsumerGroup: Option[String] = None)
  extends FlinkSource
    with FlinkIntermediateRawSource[T]
    with Serializable
    with FlinkSourceTestSupport[T]
    with RecordFormatterBaseTestDataGenerator
    with ExplicitUidInOperatorsSupport {

  protected lazy val topics: List[String] = preparedTopics.map(_.prepared)

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    val consumerGroupId = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(flinkNodeContext))
    val sourceFunction = flinkSourceFunction(consumerGroupId)

    prepareSourceStream(env, flinkNodeContext, sourceFunction)
  }

  override val typeInformation: TypeInformation[T] = {
    wrapToFlinkDeserializationSchema(deserializationSchema).getProducedType
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

  //Flink implementation of testing uses direct output from testDataParser, so we perform deserialization here, in contrast to Lite implementation
  override def testDataParser: TestDataParser[T] = (merged: TestData) =>
    formatter.parseDataForTest(topics, merged.testData).map(deserializationSchema.deserialize)

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]] = timestampAssigner

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = Some(
    passedAssigner.getOrElse(new StandardTimestampWatermarkHandler[T](WatermarkStrategy
      .forBoundedOutOfOrderness(Duration.ofMillis(kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)))))
  )

  protected def deserializeTestData(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    deserializationSchema.deserialize(record)
  }

}

class FlinkConsumerRecordBasedKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic],
                                                kafkaConfig: KafkaConfig,
                                                deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                                timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                                formatter: RecordFormatter,
                                                override val contextInitializer: ContextInitializer[ConsumerRecord[K, V]]) extends FlinkKafkaSource[ConsumerRecord[K, V]](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter) {

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]] = timestampAssigner.orElse(Some(
    StandardTimestampWatermarkHandler.afterEachEvent[ConsumerRecord[K, V]]((_.timestamp()): SimpleSerializableTimestampAssigner[ConsumerRecord[K, V]])
  ))

  override val typeInformation: TypeInformation[ConsumerRecord[K, V]] = {
    TypeInformation.of(classOf[ConsumerRecord[K, V]])
  }
}

object FlinkKafkaSource {
  val defaultMaxOutOfOrdernessMillis = 60000
}
