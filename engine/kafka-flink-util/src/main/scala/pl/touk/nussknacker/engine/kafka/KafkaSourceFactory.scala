package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._

/**<pre>
  * Wrapper for [[org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09]]
  * Features:
  *   - fetch latest N records which can be later used to test process in UI
  *     Fetching data is defined in [[pl.touk.nussknacker.engine.kafka.KafkaSourceFactory.KafkaSource]] which
  *     extends [[pl.touk.nussknacker.engine.api.process.TestDataGenerator]]. See [[pl.touk.nussknacker.engine.kafka.KafkaEspUtils#readLastMessages]]
  *   - reset Kafka's offset to latest value - `forceLatestRead` property, see [[pl.touk.nussknacker.engine.kafka.KafkaEspUtils#setOffsetToLatest]]
  *</pre>
* */
class KafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                             schema: DeserializationSchema[T],
                                             val timestampAssigner: Option[TimestampAssigner[T]],
                                             testPrepareInfo: TestDataSplit) extends FlinkSourceFactory[T] with Serializable {

  @MethodToInvoke
  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Source[T] with TestDataGenerator = {
    val espKafkaProperties = config.kafkaEspProperties.getOrElse(Map.empty)
    if (espKafkaProperties.get("forceLatestRead").exists(java.lang.Boolean.parseBoolean)) {
      KafkaEspUtils.setOffsetToLatest(topic, processMetaData.id, config)
    } else {
      ()
    }
    new KafkaSource(consumerGroupId = processMetaData.id, topic = topic)
  }

  override def testDataParser = Some(new TestDataParser[T] {
    override def parseTestData(data: Array[Byte]) = testPrepareInfo.splitData(data).map(schema.deserialize)
  })

  class KafkaSource(consumerGroupId: String, topic: String) extends FlinkSource[T] with Serializable
    with TestDataGenerator {
    override def typeInformation: TypeInformation[T] =
      implicitly[TypeInformation[T]]

    override def toFlinkSource: SourceFunction[T] = {
      new FlinkKafkaConsumer09[T](topic, schema, KafkaEspUtils.toProperties(config, Some(consumerGroupId)))
    }

    override def generateTestData(size: Int) =
      testPrepareInfo.joinData(KafkaEspUtils.readLastMessages(topic, size, config))

    override def timestampAssigner = KafkaSourceFactory.this.timestampAssigner
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

}
