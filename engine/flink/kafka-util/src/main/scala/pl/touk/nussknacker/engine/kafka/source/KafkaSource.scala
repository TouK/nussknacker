package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TestDataGenerator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.kafka._

import scala.collection.JavaConverters._

class KafkaSource[T: TypeInformation](preparedTopics: List[PreparedKafkaTopic],
                                      kafkaConfig: KafkaConfig,
                                      deserializationSchema: KafkaDeserializationSchema[T],
                                      timestampAssigner: Option[TimestampAssigner[T]],
                                      recordFormatterOpt: Option[RecordFormatter],
                                      testPrepareInfo: TestDataSplit,
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

    timestampAssigner.map {
      case periodic: AssignerWithPeriodicWatermarks[T@unchecked] =>
        newStart.assignTimestampsAndWatermarks(periodic)
      case punctuated: AssignerWithPunctuatedWatermarks[T@unchecked] =>
        newStart.assignTimestampsAndWatermarks(punctuated)
    }.getOrElse(newStart)
  }

  protected val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

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
        deserializationSchema.deserialize(new ConsumerRecord[Array[Byte], Array[Byte]](topic, -1, -1, record.key(), record.value()))
      }
  }

  override def timestampAssignerForTest: Option[TimestampAssigner[T]] = timestampAssigner
}
