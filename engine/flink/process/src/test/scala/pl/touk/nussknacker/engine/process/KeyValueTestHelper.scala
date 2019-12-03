package pl.touk.nussknacker.engine.process

import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessConfigCreator, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSourceFactory}
import pl.touk.nussknacker.engine.process.compiler.FlinkStreamingProcessCompiler
import pl.touk.nussknacker.engine.util.LoggingListener

import scala.concurrent._

object KeyValueTestHelper {

  case class KeyValue(key: String, value: Int, date: Date)

  object KeyValue {
    def apply(list: List[String]): KeyValue = {
      KeyValue(list.head, list(1).toInt, new Date(list(2).toLong))
    }
  }

  object processInvoker {

    def prepareCreator(exConfig: ExecutionConfig, data: List[KeyValue], kafkaConfig: KafkaConfig) = new ProcessConfigCreator {
      override def services(config: Config) = Map("mock" -> WithCategories(MockService))
      override def sourceFactories(config: Config) =
        Map(
          "kafka-keyvalue" -> WithCategories(new KafkaSourceFactory[KeyValue](
            kafkaConfig,
            new CsvSchema(KeyValue.apply, '|'),
            Some(new BoundedOutOfOrdernessTimestampExtractor[KeyValue](Time.minutes(10)) {
              override def extractTimestamp(element: KeyValue) = element.date.getTime
            }),
            TestParsingUtils.newLineSplit
          )
        ))
      override def sinkFactories(config: Config) = Map.empty
      override def listeners(config: Config) = Seq(LoggingListener)

      override def customStreamTransformers(config: Config) = Map()
      override def exceptionHandlerFactory(config: Config) = ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

      override def expressionConfig(config: Config) = ExpressionConfig(Map.empty, List.empty)

      override def signals(config: Config) = Map()

      override def buildInfo(): Map[String, String] = Map.empty
    }

    def invokeWithKafka(process: EspProcess, config: KafkaConfig, processVersion: ProcessVersion = ProcessVersion.empty) = {
      val env = StreamExecutionEnvironment.createLocalEnvironment(1, FlinkTestConfiguration.configuration())
      val creator = prepareCreator(env.getConfig, List.empty, config)
      val configuration = ConfigFactory.load()
      new FlinkStreamingProcessCompiler(creator, configuration).createFlinkProcessRegistrar().register(env, process, processVersion)
      MockService.data.clear()
      env.execute()
    }

  }

  object MockService extends Service {

    val data = new CopyOnWriteArrayList[Any]

    def invoke(@ParamName("input") input: Any)
              (implicit ec: ExecutionContext) =
      Future.successful(data.add(input))
  }

  // This class is not for production use as it uses String.split for csv parsing.
  // In production use more robust library - with separator escaping support etc
  class CsvSchema[T: TypeInformation](constructor: List[String] => T, separator: Char) extends DeserializationSchema[T] {
    override def isEndOfStream(t: T): Boolean = false

    override def deserialize(bytes: Array[Byte]): T = constructor(toFields(bytes))

    def toFields(bytes: Array[Byte]) = new String(bytes, StandardCharsets.UTF_8).split(separator).toList

    override def getProducedType: TypeInformation[T] = implicitly[TypeInformation[T]]
  }
}