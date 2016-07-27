package pl.touk.esp.engine.kafka

import java.util.Properties

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.esp.engine.api.{MetaData, Source, SourceFactory}
import pl.touk.esp.engine.kafka.KafkaSourceFactory._

class KafkaSourceFactory[T: TypeInformation](schema: DeserializationSchema[T],
                                             properties: Properties) extends SourceFactory {

  override def create(processMetaData: MetaData, parameters: Map[String, String]): Source = {
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      topic = parameters(TopicParamName)
    )
  }

  class KafkaSource(consumerGroupId: String, topic: String) extends Source {
    override def typeInformation: TypeInformation[Any] =
      implicitly[TypeInformation[T]].asInstanceOf[TypeInformation[Any]]

    override def toFlinkSource: SourceFunction[Any] = {
      val propertiesCopy = new Properties()
      propertiesCopy.putAll(properties)
      propertiesCopy.setProperty("group.id", consumerGroupId)
      new FlinkKafkaConsumer09[T](topic, schema, propertiesCopy).asInstanceOf[SourceFunction[Any]]
    }
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

}