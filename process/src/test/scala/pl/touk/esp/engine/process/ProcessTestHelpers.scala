package pl.touk.esp.engine.process

import java.util.Date

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.InterpreterConfig
import pl.touk.esp.engine.api.process.{SinkFactory, SourceFactory}
import pl.touk.esp.engine.api.{FoldingFunction, Service, BrieflyLoggingExceptionHandler}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.util.CollectionSource
import pl.touk.esp.engine.util.sink.ServiceSink

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object ProcessTestHelpers {


  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  object processInvoker {
    def invoke(process: EspProcess, data: List[SimpleRecord],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val monitorSink = new ServiceSink(EmptyService)
      val sinkFactories = Map[String, SinkFactory](
        "monitor" -> SinkFactory.noParam(monitorSink)
      )
      new FlinkProcessRegistrar(
        interpreterConfig = () => new InterpreterConfig(Map("logService" -> MockService)),
        sourceFactories = Map("input" -> SourceFactory.noParam(new CollectionSource[SimpleRecord](env.getConfig, data, Some((a: SimpleRecord) => a.date.getTime)))),
        sinkFactories = sinkFactories,
        foldingFunctions = Map("simpleFoldingFun" -> SimpleRecordFoldingFunction),
        processTimeout = 2 minutes,
        espExceptionHandlerProvider = () => BrieflyLoggingExceptionHandler
      ).register(env, process)

      MockService.data.clear()
      env.execute()

    }
  }

  object MockService extends Service {

    val data = new ArrayBuffer[Map[String, Any]]

    override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext) = Future {
      data.append(params)
    }
  }


  object EmptyService extends Service {
    override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext) = Future(())
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
