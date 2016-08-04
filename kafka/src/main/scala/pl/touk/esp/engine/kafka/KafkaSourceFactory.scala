package pl.touk.esp.engine.kafka

import java.util.Properties

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.esp.engine.api.process.{SourceFactory, Source}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.kafka.KafkaSourceFactory._

class KafkaSourceFactory[T: TypeInformation](schema: DeserializationSchema[T],
                                             properties: Properties) extends SourceFactory[T] {

  override def create(processMetaData: MetaData, parameters: Map[String, String]): Source[T] = {
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      topic = parameters(TopicParamName)
    )
  }

  class KafkaSource(consumerGroupId: String, topic: String) extends Source[T] {
    override def typeInformation: TypeInformation[T] =
      implicitly[TypeInformation[T]]

    override def toFlinkSource: SourceFunction[T] = {
      val propertiesCopy = new Properties()
      propertiesCopy.putAll(properties)
      propertiesCopy.setProperty("group.id", consumerGroupId)
      new FlinkKafkaConsumer09[T](topic, schema, propertiesCopy)
    }
  }

}

object KafkaSourceFactory {

  final val TopicParamName = "topic"

}