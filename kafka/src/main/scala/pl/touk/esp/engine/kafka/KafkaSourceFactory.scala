package pl.touk.esp.engine.kafka

import java.util.Properties

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.esp.engine.api.process.Source
import pl.touk.esp.engine.api.{MetaData, ParamName}
import pl.touk.esp.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
import pl.touk.esp.engine.kafka.KafkaSourceFactory._

import scala.collection.JavaConverters._

class KafkaSourceFactory[T: TypeInformation](config: KafkaConfig,
                                             schema: DeserializationSchema[T],
                                             timestampAssigner: Option[TimestampAssigner[T]]) extends FlinkSourceFactory[T] with Serializable {

  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Source[T] = {
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      topic = topic
    )
  }

  class KafkaSource(consumerGroupId: String, topic: String) extends FlinkSource[T] with Serializable {
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
      config.kafkaProperties.map(_.asJava).foreach(props.putAll)
      props
    }

    override def timestampAssigner = KafkaSourceFactory.this.timestampAssigner
  }

  override val testDataParser : Option[String => T] = Some(testData => schema.deserialize(testData.getBytes))

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

}
