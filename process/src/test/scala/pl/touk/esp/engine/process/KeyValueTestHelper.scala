package pl.touk.esp.engine.process

import java.util.Date

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SourceFactory}
import pl.touk.esp.engine.api.{BrieflyLoggingExceptionHandler, ParamName, Service}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.util.CollectionSource
import pl.touk.esp.engine.util.LoggingListener

import scala.collection.mutable.ArrayBuffer
import scala.concurrent._

object KeyValueTestHelper {

  case class KeyValue(key: String, value: Int, date: Date)

  object processInvoker {

    def prepareCreator(exConfig: ExecutionConfig, data: List[KeyValue]) = new ProcessConfigCreator {
      override def services(config: Config) = Map("mock" -> MockService)
      override def sourceFactories(config: Config) =
        Map("simple-keyvalue" -> SourceFactory.noParam(new CollectionSource[KeyValue](exConfig, data, Some(_.date.getTime))))
      override def sinkFactories(config: Config) = Map.empty
      override def listeners(config: Config) = Seq(LoggingListener)
      override def foldingFunctions(config: Config) = Map.empty
      override def exceptionHandler(config: Config) = BrieflyLoggingExceptionHandler
    }

    def invoke(process: EspProcess, data: List[KeyValue],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, data)
      FlinkProcessRegistrar(creator, ConfigFactory.load()).register(env, process)
      MockService.data.clear()
      env.execute()
    }

  }

  object MockService extends Service {

    val data = new ArrayBuffer[Any]

    def invoke(@ParamName("input") input: Any)
              (implicit ec: ExecutionContext) =
      Future.successful(data.append(input))
  }

}