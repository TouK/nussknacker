package pl.touk.esp.engine.kafka

import java.util.Properties

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.esp.engine.api.process.{SourceFactory, Source}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.kafka.KafkaSourceFactory._

class KafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                             schema: DeserializationSchema[T]) extends SourceFactory[T] with Serializable {

  override def create(processMetaData: MetaData, parameters: Map[String, String]): Source[T] = {
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      topic = parameters(KafkaSourceFactory.TopicParamName)
    )
  }

  class KafkaSource(consumerGroupId: String, topic: String) extends Source[T] with Serializable {
    override def typeInformation: TypeInformation[T] =
      implicitly[TypeInformation[T]]

    override def toFlinkSource: SourceFunction[T] = {
      val propertiesCopy = new Properties()
      propertiesCopy.putAll(kafkaProperties())
      propertiesCopy.setProperty("group.id", consumerGroupId)
      new FlinkKafkaConsumer09[T](topic, schema, propertiesCopy)
    }

    private def kafkaProperties(): Properties = {
      val props = new Properties()
      props.setProperty("zookeeper.connect", config.zkAddress)
      props.setProperty("bootstrap.servers", config.kafkaAddress)
      props.setProperty("auto.offset.reset", "earliest")
      props
    }
  }

}

object KafkaSourceFactory {

  case class KafkaConfig(zkAddress: String, kafkaAddress: String)
  val TopicParamName = "topic"

}
