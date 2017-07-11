package pl.touk.esp.engine.example

import java.util.UUID

import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import argonaut.DecodeJson
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory, WithCategories}
import pl.touk.esp.engine.api.signal.ProcessSignalSender
import pl.touk.esp.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.esp.engine.example.custom.{EventsCounter, TransactionAmountAggregator}
import pl.touk.esp.engine.example.service.ClientService
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.flink.util.source.EspDeserializationSchema
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSinkFactory, KafkaSourceFactory}
import pl.touk.esp.engine.util.LoggingListener

class ExampleProcessConfigCreator extends ProcessConfigCreator {

  def marketing[T](value: T) = WithCategories(value, List("Recommendations"))
  def fraud[T](value: T) = WithCategories(value, List("FraudDetection"))
  def all[T](value: T) = WithCategories(value, List("Recommendations", "FraudDetection"))

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map(
      "transactionAmountAggregator" -> all(new TransactionAmountAggregator),
      "eventsCounter" -> all(new EventsCounter)
    )
  }

  override def services(config: Config): Map[String, WithCategories[Service]] = {
    Map(
      "clientService" -> all(new ClientService)
    )
  }

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    val transactionSource = createTransactionSource(kafkaConfig)
    Map("kafka-transaction" -> all(transactionSource))
  }

  private def createTransactionSource(kafkaConfig: KafkaConfig) = {
    val transactionTimestampExtractor = new BoundedOutOfOrdernessTimestampExtractor[Transaction](Time.minutes(10)) {
      override def extractTimestamp(element: Transaction): Long = element.eventDate
    }
    kafkaSource[Transaction](kafkaConfig, jsonBytes => {
      val decoder = implicitly[DecodeJson[Transaction]]
      new String(jsonBytes).decodeOption(decoder).get
    }, Some(transactionTimestampExtractor), TestParsingUtils.emptyLineSplit)
  }

  private def kafkaSource[T: TypeInformation](config: KafkaConfig,
                                              decode: Array[Byte] => T,
                                              timestampAssigner: Option[TimestampAssigner[T]],
                                              testPrepareInfo: TestDataSplit): SourceFactory[T] = {
    val schema = new EspDeserializationSchema[T](bytes => decode(bytes))
    new KafkaSourceFactory[T](config, schema, timestampAssigner , testPrepareInfo)
  }

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
    val kafkaConfig = config.as[KafkaConfig]("kafka")
    val stringSink = kafkaSink(kafkaConfig, new KeyedSerializationSchema[Any] {
      override def serializeKey(element: Any): Array[Byte] = UUID.randomUUID().toString.getBytes()
      override def serializeValue(element: Any): Array[Byte] = element.asInstanceOf[String].getBytes()
      override def getTargetTopic(element: Any): String = null
    })
    Map(
      "kafka-stringSink" -> all(stringSink)
    )
  }

  private def kafkaSink(kafkaConfig: KafkaConfig, serializationSchema: KeyedSerializationSchema[Any]) : SinkFactory = {
    new KafkaSinkFactory(kafkaConfig, serializationSchema)
  }

  override def listeners(config: Config): Seq[ProcessListener] = {
    Seq(LoggingListener)
  }

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory = {
    new LoggingExceptionHandlerFactory
  }

  override def globalProcessVariables(config: Config): Map[String, WithCategories[AnyRef]] = {
    Map(
      "UTIL" -> all(UtilProcessHelper)
    )
  }

  override def buildInfo(): Map[String, String] = {
    val engineBuildInfo = pl.touk.esp.engine.version.BuildInfo.toMap.map { case (k, v) => s"engine-$k" -> v.toString }
    engineBuildInfo ++ Map(
      "process-version" -> "0.1"
    )
  }

  override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = {
    Map.empty //TODO
  }
}

class LoggingExceptionHandlerFactory extends ExceptionHandlerFactory {

  @MethodToInvoke
  def create(metaData: MetaData, @ParamName("sampleParam") sampleParam: String) = {
    VerboselyLoggingExceptionHandler(metaData, params = Map("sampleParam" -> sampleParam))
  }

}