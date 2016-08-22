package pl.touk.esp.engine.perftest.sample

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.streaming.util.serialization.{KeyedSerializationSchema, SerializationSchema}
import pl.touk.esp.engine.api.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.kafka.KafkaConfig
import pl.touk.esp.engine.kafka.{KafkaSinkFactory, KafkaSourceFactory}
import pl.touk.esp.engine.perftest.sample.model.KeyValue
import pl.touk.esp.engine.util.CsvSchema

class AggProcessConfigCreator extends ProcessConfigCreator {

  import org.apache.flink.streaming.api.scala._

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    Map(
      "kafka-keyvalue" -> new KafkaSourceFactory[KeyValue](kafkaConfig, new CsvSchema(KeyValue.apply), Some(_.date.getTime))
    )
  }

  override def sinkFactories(config: Config) = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    val longSerializationSchema = new KeyedSerializationSchema[Any] {

      override def serializeValue(element: Any) =
        element.asInstanceOf[Long].toString.getBytes

      override def serializeKey(element: Any) =
        element.asInstanceOf[Long].toString.getBytes

      override def getTargetTopic(element: Any) = null
    }
    Map(
      "kafka-long" -> new KafkaSinkFactory(kafkaConfig.kafkaAddress, longSerializationSchema)
    )
  }

  override def services(config: Config) = Map.empty

  override def foldingFunctions(config: Config) = Map.empty

  override def exceptionHandler(config: Config) = VerboselyLoggingExceptionHandler

}