package pl.touk.nussknacker.engine.demo

import java.time.Duration

import com.typesafe.config.Config
import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, _}
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.demo.custom.{EventsCounter, TransactionAmountAggregator}
import pl.touk.nussknacker.engine.demo.service.{AlertService, ClientService}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.flink.util.transformer.{TransformStateTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{FixedRecordFormatterFactoryWrapper, JsonRecordFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.util.LoggingListener

import scala.reflect.{ClassTag, classTag}

class DemoProcessConfigCreator extends ProcessConfigCreator {

  def marketing[T](value: T): WithCategories[T] = WithCategories(value, "Recommendations")
  def fraud[T](value: T): WithCategories[T] = WithCategories(value, "FraudDetection")
  def all[T](value: T): WithCategories[T] = WithCategories(value, "Recommendations", "FraudDetection")

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    Map(
      "transactionAmountAggregator" -> all(new TransactionAmountAggregator),
      "eventsCounter" -> all(new EventsCounter),
      "union" -> all(UnionTransformer),
      "state" -> all(TransformStateTransformer)
    )
  }

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = {
    Map(
      "clientService" -> all(new ClientService),
      "alertService" -> all(new AlertService("/tmp/alerts"))
    )
  }

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val transactionSource = createTransactionSource(processObjectDependencies)
    val clientSource = createClientSource(processObjectDependencies)
    Map(
      "kafka-transaction" -> all(transactionSource),
      "kafka-client" -> all(clientSource)
    )
  }

  private def createTransactionSource(processObjectDependencies: ProcessObjectDependencies) = {
    val transactionTimestampExtractor = StandardTimestampWatermarkHandler.boundedOutOfOrderness[ConsumerRecord[String, Transaction]](_.value().eventDate, Duration.ofMinutes(10))
    kafkaSource[Transaction](decodeJsonUnsafe[Transaction](_), Some(transactionTimestampExtractor), processObjectDependencies)
  }

  private def createClientSource(processObjectDependencies: ProcessObjectDependencies) = {
    kafkaSource[Client](decodeJsonUnsafe[Client](_), None,processObjectDependencies)
  }

  private def kafkaSource[T: TypeInformation](decode: Array[Byte] => T,
                                              timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[String, T]]],
                                              processObjectDependencies: ProcessObjectDependencies): FlinkSourceFactory[ConsumerRecord[String, T]] = {
    val schema = new EspDeserializationSchema[T](bytes => decode(bytes))
    val schemaFactory = new FixedValueDeserializationSchemaFactory(schema)
    new KafkaSourceFactory[String, T](schemaFactory, timestampAssigner, FixedRecordFormatterFactoryWrapper(JsonRecordFormatter), processObjectDependencies)(classTag[String], ClassTag(implicitly[TypeInformation[T]].getTypeClass))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    val stringOrJsonSink = kafkaSink(new SimpleSerializationSchema[Any](_, {
      case a: DisplayJson => a.asJson.noSpaces
      case a: Json => a.noSpaces
      case a: String => a
      case _ => throw new RuntimeException("Sorry, only strings or json are supported...")
    }), processObjectDependencies)
    Map("kafka-stringSink" -> all(stringOrJsonSink))
  }

  private def kafkaSink(serializationSchema: String => KafkaSerializationSchema[Any],
                        processObjectDependencies: ProcessObjectDependencies) : SinkFactory = {
    new KafkaSinkFactory(serializationSchema, processObjectDependencies)
  }

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = {
    Seq(LoggingListener)
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    new LoggingExceptionHandlerFactory(processObjectDependencies.config)

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val globalProcessVariables = Map(
      "UTIL" -> all(UtilProcessHelper),
      "TYPES" -> all(DataTypes)
    )
    ExpressionConfig(globalProcessVariables, List.empty)
  }

  override def buildInfo(): Map[String, String] = {
    val engineBuildInfo = pl.touk.nussknacker.engine.version.BuildInfo.toMap.map { case (k, v) => s"engine-$k" -> v.toString }
    engineBuildInfo ++ Map(
      "process-version" -> "0.1"
    )
  }

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = {
    Map.empty //TODO
  }
}

class LoggingExceptionHandlerFactory(config: Config) extends ExceptionHandlerFactory {

  @MethodToInvoke
  def create(metaData: MetaData): EspExceptionHandler =
    BrieflyLoggingExceptionHandler(metaData)

}
