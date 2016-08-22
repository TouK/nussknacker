package pl.touk.esp.engine.process

import java.util.Date

import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.InterpreterConfig
import pl.touk.esp.engine.api.process.SourceFactory
import pl.touk.esp.engine.api.{BrieflyLoggingExceptionHandler, ParamName, Service}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.util.CollectionSource

import scala.collection.mutable.ArrayBuffer
import scala.concurrent._
import scala.concurrent.duration._

object KeyValueTestHelper {

  case class KeyValue(key: String, value: Int, date: Date)

  object processInvoker {
    def invoke(process: EspProcess, data: List[KeyValue],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      new FlinkProcessRegistrar(
        interpreterConfig = () => new InterpreterConfig(Map("mock" -> MockService)),
        sourceFactories = Map("simple-keyvalue" -> SourceFactory.noParam(new CollectionSource[KeyValue](env.getConfig, data, Some(_.date.getTime)))),
        sinkFactories = Map.empty,
        foldingFunctions = Map.empty,
        processTimeout = 2 minutes,
        espExceptionHandlerProvider = () => BrieflyLoggingExceptionHandler
      ).register(env, process)

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