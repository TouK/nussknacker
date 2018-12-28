package pl.touk.nussknacker.engine.example

import java.nio.charset.StandardCharsets
import java.util.UUID

import argonaut._
import Argonaut._
import ArgonautShapeless._
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.example.custom.{EventsCounter, TransactionAmountAggregator}
import pl.touk.nussknacker.engine.example.service.{AlertService, ClientService}
import pl.touk.nussknacker.engine.flink.util.exception.VerboselyLoggingRestartingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory, KafkaSourceFactory}
import pl.touk.nussknacker.engine.util.LoggingListener

class ExampleProcessConfigCreator extends ProcessConfigCreator {

  def marketing[T](value: T) = WithCategories(value, "Recommendations")
  def fraud[T](value: T) = WithCategories(value, "FraudDetection")
  def all[T](value: T) = WithCategories(value, "Recommendations", "FraudDetection")

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map(
      "transactionAmountAggregator" -> all(new TransactionAmountAggregator),
      "eventsCounter" -> all(new EventsCounter)
    )
  }

  override def services(config: Config): Map[String, WithCategories[Service]] = {
    Map(
      "clientService" -> all(new ClientService),
      "alertService" -> all(new AlertService("/tmp/alerts"))
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
      new String(jsonBytes, StandardCharsets.UTF_8).decodeOption(decoder).get
    }, Some(transactionTimestampExtractor), TestParsingUtils.newLineSplit)
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
    val stringOrJsonSink = kafkaSink(kafkaConfig, new KeyedSerializationSchema[Any] {
      override def serializeKey(element: Any): Array[Byte] = UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8)
      override def serializeValue(element: Any): Array[Byte] = element match {
        case a:Displayable => a.display.nospaces.getBytes(StandardCharsets.UTF_8)
        case a:Json => a.nospaces.getBytes(StandardCharsets.UTF_8)
        case a:String => a.getBytes(StandardCharsets.UTF_8)
        case _ => throw new RuntimeException("Sorry, only strings or json are supported...")
      }
      override def getTargetTopic(element: Any): String = null
    })
    Map(
      "kafka-stringSink" -> all(stringOrJsonSink)
    )
  }

  private def kafkaSink(kafkaConfig: KafkaConfig, serializationSchema: KeyedSerializationSchema[Any]) : SinkFactory = {
    new KafkaSinkFactory(kafkaConfig, serializationSchema)
  }

  override def listeners(config: Config): Seq[ProcessListener] = {
    Seq(LoggingListener)
  }

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory = {
    new LoggingExceptionHandlerFactory(config)
  }

  override def expressionConfig(config: Config) = {
    val globalProcessVariables = Map(
      "UTIL" -> all(UtilProcessHelper)
    )
    ExpressionConfig(globalProcessVariables, List.empty)
  }

  override def buildInfo(): Map[String, String] = {
    val engineBuildInfo = pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => s"engine-$k" -> v.toString }
    engineBuildInfo ++ Map(
      "process-version" -> "0.1"
    )
  }

  override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = {
    Map.empty //TODO
  }
}

class LoggingExceptionHandlerFactory(config: Config) extends ExceptionHandlerFactory {

  @MethodToInvoke
  def create(metaData: MetaData, @ParamName("sampleParam") sampleParam: String) = {
    VerboselyLoggingRestartingExceptionHandler(metaData, config, params = Map("sampleParam" -> sampleParam))
  }

}