package pl.touk.esp.engine.process

import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.LazyInterpreter
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.esp.engine.flink.api.process.{FlinkSink, FlinkSourceFactory}
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.api.WithExceptionHandler
import pl.touk.esp.engine.util.{LoggingListener, SynchronousExecutionContext}
import pl.touk.esp.engine.flink.util.source.CollectionSource

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

object ProcessTestHelpers {


  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date, value3Opt: Option[BigDecimal] = None, value3: BigDecimal = 1, intAsAny: Any = 1)

  case class SimpleRecordWithPreviousValue(record: SimpleRecord, previous: Long, added: String)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  object processInvoker {
    def invoke(process: EspProcess, data: List[SimpleRecord],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, data)
      FlinkProcessRegistrar(creator, ConfigFactory.load()).register(env, process)

      MockService.clear()
      env.execute()

    }

    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]) = new ProcessConfigCreator {
      override def services(config: Config) = Map("logService" -> WithCategories(MockService))

      override def sourceFactories(config: Config) = Map(
        "input" -> WithCategories(FlinkSourceFactory.noParam(new CollectionSource[SimpleRecord](
          config = exConfig,
          list = data,
          timestampAssigner = Some(new AscendingTimestampExtractor[SimpleRecord] {
            override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
          })
        )))
      )

      override def sinkFactories(config: Config) = Map(
        "monitor" -> WithCategories(SinkFactory.noParam(EmptySink))
      )

      override def customStreamTransformers(config: Config) = Map("stateCustom" -> WithCategories(StateCustomNode))

      override def listeners(config: Config) = Seq(LoggingListener)

      override def exceptionHandlerFactory(config: Config) = ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler)

      override def globalProcessVariables(config: Config) = {
        Map("processHelper" -> WithCategories(ProcessHelper.getClass))
      }
    }
  }

  object StateCustomNode extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@ParamName("keyBy") keyBy: LazyInterpreter[SimpleRecord],
               @ParamName("stringVal") stringVal: String)(exceptionHander: ()=>FlinkEspExceptionHandler) = (start: DataStream[InterpretationResult], timeout: FiniteDuration) => {

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

  case class CustomMap(lazyHandler: ()=>FlinkEspExceptionHandler) extends RichMapFunction[Any, Any] with WithExceptionHandler {
    override def map(value: Any) = {
       //tu nic madrego nie robimy, tylko zeby zobaczyc czy Exceptionhandler jest wstrzykniety
       exceptionHandler.recover(value)(Context()).getOrElse(0)
    }
  }

  object MockService extends Service {

    private val data_ = new CopyOnWriteArrayList[Any]
    def data = {
      data_.toArray.toList
    }

    def clear() = {
      data_.clear()
    }

    def invoke(@ParamName("all") all: Any) = {
      Future.successful {
        data_.add(all)
      }
    }
  }

  case object EmptySink extends FlinkSink {
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {}
    }
  }

  object EmptyService extends Service {
    def invoke() = Future.successful(Unit)
  }

}

object ProcessHelper {
  val constant = 4
  def add(a: Int, b: Int) = {
    a + b
  }
}
