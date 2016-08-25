package pl.touk.esp.engine.process

import java.util.Date

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, Sink, SinkFactory, SourceFactory}
import pl.touk.esp.engine.api.{BrieflyLoggingExceptionHandler, FoldingFunction, ParamName, Service}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.util.LoggingListener
import pl.touk.esp.engine.util.source.CollectionSource

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object ProcessTestHelpers {


  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  object processInvoker {
    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]) = new ProcessConfigCreator {
      override def services(config: Config) = Map("logService" -> MockService)
      override def sourceFactories(config: Config) = Map(
        "input" -> SourceFactory.noParam(new CollectionSource[SimpleRecord](exConfig, data, Some((a: SimpleRecord) => a.date.getTime)))
      )
      override def sinkFactories(config: Config) = Map(
        "monitor" -> SinkFactory.noParam(EmptySink)
      )
      override def listeners(config: Config) = Seq(LoggingListener)
      override def foldingFunctions(config: Config) = Map("simpleFoldingFun" -> SimpleRecordFoldingFunction)
      override def exceptionHandler(config: Config) = BrieflyLoggingExceptionHandler
    }

    def invoke(process: EspProcess, data: List[SimpleRecord],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, data)
      FlinkProcessRegistrar(creator, ConfigFactory.load()).register(env, process)

      MockService.data.clear()
      env.execute()

    }
  }

  object MockService extends Service {

    val data = new ArrayBuffer[Any]

    def invoke(@ParamName("all") all: Any) = Future.successful {
      data.append(all)
    }
  }

  case object EmptySink extends Sink {
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {}
    }
  }

  object EmptyService extends Service {
    def invoke() = Future.successful(Unit)
  }

  object SimpleRecordFoldingFunction extends FoldingFunction[SimpleRecordAcc] {
    override def fold(value: AnyRef, acc: Option[SimpleRecordAcc]) = {
      val srv = value.asInstanceOf[SimpleRecord]
      acc match {
        case Some(old) => SimpleRecordAcc(old.id, old.value1 + srv.value1, old.value2 + srv.value2, srv.date)
        case None => SimpleRecordAcc(srv.id, srv.value1, Set(srv.value2), srv.date)
      }
    }
  }

}
