package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer011, FlinkKafkaConsumer09}
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._

/** <pre>
  * Wrapper for [[org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09]]
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
                                             schema: DeserializationSchema[T],
                                             timestampAssigner: Option[TimestampAssigner[T]],
                                             testPrepareInfo: TestDataSplit) extends BaseKafkaSourceFactory(config, schema, timestampAssigner, testPrepareInfo) {

  @MethodToInvoke
  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Source[T] with TestDataGenerator = {
    createSource(processMetaData, topic)
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

}


class SingleTopicKafkaSourceFactory[T: TypeInformation](topic: String,
                                                        config: KafkaConfig,
                                                        schema: DeserializationSchema[T],
                                                        timestampAssigner: Option[TimestampAssigner[T]],
                                                        testPrepareInfo: TestDataSplit) extends BaseKafkaSourceFactory(config, schema, timestampAssigner, testPrepareInfo) {

  @MethodToInvoke
  def create(processMetaData: MetaData): Source[T] with TestDataGenerator = {
    createSource(processMetaData, topic)
  }

}

abstract class BaseKafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                                          schema: DeserializationSchema[T],
                                                          val timestampAssigner: Option[TimestampAssigner[T]],
                                                          testPrepareInfo: TestDataSplit) extends FlinkSourceFactory[T] with Serializable {


  override def testDataParser: Option[TestDataParser[T]] = Some(new TestDataParser[T] {
    override def parseTestData(data: Array[Byte]): List[T] = testPrepareInfo.splitData(data).map(schema.deserialize)
  })

  protected def createSource(processMetaData: MetaData, topic: String): KafkaSource = {
    new KafkaSource(consumerGroupId = processMetaData.id, topic = topic)
  }

  class KafkaSource(consumerGroupId: String, topic: String) extends FlinkSource[T] with Serializable with TestDataGenerator {

    override def typeInformation: TypeInformation[T] =
      implicitly[TypeInformation[T]]

    override def toFlinkSource: SourceFunction[T] = {
      //TODO: is this the best place to do it?
      setToLatestOffsetIfNeeded(topic, consumerGroupId)
      new FlinkKafkaConsumer011[T](topic, schema, KafkaEspUtils.toProperties(config, Some(consumerGroupId)))
    }

    private def setToLatestOffsetIfNeeded(topic: String, consumerGroupId: String): Unit = {
      val setToLatestOffset =
        config.kafkaEspProperties.flatMap(_.get("forceLatestRead")).exists(java.lang.Boolean.parseBoolean)
      if (setToLatestOffset) {
        KafkaEspUtils.setOffsetToLatest(topic, consumerGroupId, config)
      }
    }

    override def generateTestData(size: Int): Array[Byte] =
      testPrepareInfo.joinData(KafkaEspUtils.readLastMessages(topic, size, config))

    override def timestampAssigner: Option[TimestampAssigner[T]] = BaseKafkaSourceFactory.this.timestampAssigner
  }

}