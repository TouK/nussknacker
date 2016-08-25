package pl.touk.esp.engine.process

import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SourceFactory}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSourceFactory}
import pl.touk.esp.engine.util.LoggingListener
import pl.touk.esp.engine.util.source.{CollectionSource, CsvSchema}

import scala.concurrent._

object KeyValueTestHelper {

  case class KeyValue(key: String, value: Int, date: Date)

  object KeyValue {
    def apply(list: List[String]): KeyValue = {
      KeyValue(list.head, list(1).toInt, new Date(list(2).toLong))
    }
  }

  case object Sum extends FoldingFunction[Int] {
    override def fold(value: AnyRef, acc: Option[Int]) = {
      acc.getOrElse(0) + value.asInstanceOf[KeyValue].value
    }
  }

  object processInvoker {

    def prepareCreator(exConfig: ExecutionConfig, data: List[KeyValue], kafkaConfig: KafkaConfig) = new ProcessConfigCreator {
      override def services(config: Config) = Map("mock" -> MockService)
      override def sourceFactories(config: Config) =
        Map(
          "simple-keyvalue" -> SourceFactory.noParam(new CollectionSource[KeyValue](exConfig, data, Some(_.date.getTime))),
          "kafka-keyvalue" -> new KafkaSourceFactory[KeyValue](kafkaConfig, new CsvSchema(KeyValue.apply), Some(_.date.getTime))
        )
      override def sinkFactories(config: Config) = Map.empty
      override def listeners(config: Config) = Seq(LoggingListener)
      override def foldingFunctions(config: Config) = Map("sum" -> Sum)
      override def exceptionHandler(config: Config) = VerboselyLoggingExceptionHandler
    }

    def invoke(process: EspProcess, data: List[KeyValue],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, data, null)
      FlinkProcessRegistrar(creator, ConfigFactory.load()).register(env, process)
      MockService.data.clear()
      env.execute()
    }


    def invokeWithKafka(process: EspProcess, config: KafkaConfig,
                        env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, List.empty, config)
      FlinkProcessRegistrar(creator, ConfigFactory.load()).register(env, process)
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

}