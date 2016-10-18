package pl.touk.esp.engine.process

import java.util.Date

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.LazyInterpreter
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, Sink, SinkFactory, SourceFactory}
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.api.WithExceptionHandler
import pl.touk.esp.engine.util.{LoggingListener, SynchronousExecutionContext}
import pl.touk.esp.engine.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.util.source.CollectionSource

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

object ProcessTestHelpers {


  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date)

  case class SimpleRecordWithPreviousValue(record: SimpleRecord, previous: Long, added: String)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  object processInvoker {
    def invoke(process: EspProcess, data: List[SimpleRecord],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, data)
      FlinkProcessRegistrar(creator, ConfigFactory.load()).register(env, process)

      MockService.data.clear()
      env.execute()

    }

    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]) = new ProcessConfigCreator {
      override def services(config: Config) = Map("logService" -> MockService)

      override def sourceFactories(config: Config) = Map(
        "input" -> SourceFactory.noParam(new CollectionSource[SimpleRecord](
          config = exConfig,
          list = data,
          timestampAssigner = Some(new AscendingTimestampExtractor[SimpleRecord] {
            override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
          })
        ))
      )

      override def sinkFactories(config: Config) = Map(
        "monitor" -> SinkFactory.noParam(EmptySink)
      )

      override def customStreamTransformers(config: Config) = Map("stateCustom" -> StateCustomNode)

      override def listeners(config: Config) = Seq(LoggingListener)

      override def foldingFunctions(config: Config) = Map("simpleFoldingFun" -> SimpleRecordFoldingFunction)

      override def exceptionHandlerFactory(config: Config) = ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler)
    }
  }

  object StateCustomNode extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("keyBy") keyBy: LazyInterpreter[SimpleRecord],
               @ParamName("stringVal") stringVal: String)(exceptionHander: ()=>EspExceptionHandler) = (start: DataStream[InterpretationResult], timeout: FiniteDuration) => {

      start.keyBy(keyBy.syncInterpretationFunction)
        .flatMapWithState[Any, Long] {
        case (SimpleFromIr(sr), Some(oldState)) => (List(SimpleRecordWithPreviousValue(sr, oldState, stringVal)), Some(sr.value1))
        case (SimpleFromIr(sr), None) =>  (List(SimpleRecordWithPreviousValue(sr, 0, stringVal)), Some(sr.value1))
      }.map(CustomMap(exceptionHander))

    }

    object SimpleFromIr {
      def unapply(ir:InterpretationResult) = Some(ir.finalContext.apply[SimpleRecord]("input"))
    }

  }

  case class CustomMap(lazyHandler: ()=>EspExceptionHandler) extends RichMapFunction[Any, Any] with WithExceptionHandler {
    override def map(value: Any) = {
       //tu nic madrego nie robimy, tylko zeby zobaczyc czy Exceptionhandler jest wstrzykniety
       exceptionHandler.recover(value)(Context()).getOrElse(0)
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
