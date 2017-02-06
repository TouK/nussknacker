package pl.touk.esp.engine.kafka

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.esp.engine.api.process.{Source, TestDataGenerator}
import pl.touk.esp.engine.api.test.{TestDataParser, TestDataSplit}
import pl.touk.esp.engine.api.{MetaData, ParamName}
import pl.touk.esp.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
import pl.touk.esp.engine.kafka.KafkaSourceFactory._


class KafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                             schema: DeserializationSchema[T],
                                             timestampAssigner: Option[TimestampAssigner[T]],
                                             testPrepareInfo: TestDataSplit) extends FlinkSourceFactory[T] with Serializable {

  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Source[T] with TestDataGenerator = {
    val espKafkaProperties = config.kafkaEspProperties.getOrElse(Map.empty)
    if (espKafkaProperties.get("forceLatestRead").exists(java.lang.Boolean.parseBoolean)) {
      //moznaby definiowac chec resetowania offsetu przy definicji procesu, ale nie jestem pewien czy tak chcemy?
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
