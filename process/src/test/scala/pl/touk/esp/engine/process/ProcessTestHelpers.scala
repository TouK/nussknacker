package pl.touk.esp.engine.process

import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, ExceptionHandlerFactory, NonTransientException}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.signal.ProcessSignalSender
import pl.touk.esp.engine.api.{LazyInterpreter, _}
import pl.touk.esp.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.esp.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkSink, FlinkSourceFactory}
import pl.touk.esp.engine.flink.api.state.WithExceptionHandler
import pl.touk.esp.engine.flink.util.exception._
import pl.touk.esp.engine.flink.util.listener.NodeCountMetricListener
import pl.touk.esp.engine.flink.util.service.TimeMeasuringService
import pl.touk.esp.engine.flink.util.source.CollectionSource
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.esp.engine.util.LoggingListener

import scala.concurrent.{ExecutionContext, Future}

object ProcessTestHelpers {


  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date, value3Opt: Option[BigDecimal] = None, value3: BigDecimal = 1, intAsAny: Any = 1)

  case class SimpleRecordWithPreviousValue(record: SimpleRecord, previous: Long, added: String)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  case class SimpleJsonRecord(id: String, field: String)

  object processInvoker {
    def invoke(process: EspProcess, data: List[SimpleRecord],
               env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, data)
      new StandardFlinkProcessCompiler(creator, ConfigFactory.load()).createFlinkProcessRegistrar().register(env, process)

      MockService.clear()
      env.execute(process.id)

    }

    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]) = new ProcessConfigCreator {

      override def services(config: Config) = Map("logService" -> WithCategories(new MockService))

      override def sourceFactories(config: Config) = Map(
        "input" -> WithCategories(FlinkSourceFactory.noParam(new CollectionSource[SimpleRecord](
          config = exConfig,
          list = data,
          timestampAssigner = Some(new AscendingTimestampExtractor[SimpleRecord] {
            override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
          })
        ))
      ))

      override def sinkFactories(config: Config) = Map(
        "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink)),
        "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts))
      )

      override def customStreamTransformers(config: Config) = Map(
        "stateCustom" -> WithCategories(StateCustomNode),
        "customFilter" -> WithCategories(CustomFilter),
        "customContextClear" -> WithCategories(CustomContextClear)
      )

      override def listeners(config: Config) = Seq(LoggingListener, new NodeCountMetricListener)

      override def exceptionHandlerFactory(config: Config) =
        ExceptionHandlerFactory.noParams(RecordingExceptionHandler(_))

      override def globalProcessVariables(config: Config) = {
        Map("processHelper" -> WithCategories(ProcessHelper.getClass))
      }

      override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map()
      override def buildInfo(): Map[String, String] = Map.empty
    }
  }

  case class RecordingExceptionHandler(metaData: MetaData) extends FlinkEspExceptionHandler with ConsumingNonTransientExceptions {
    override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()


    override protected val consumer: FlinkEspExceptionConsumer = new RateMeterExceptionConsumer(new FlinkEspExceptionConsumer {
      override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit =
        RecordingExceptionHandler.add(exceptionInfo)
    })
  }

  object RecordingExceptionHandler extends WithDataList[EspExceptionInfo[_ <: Throwable]]

  object StateCustomNode extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[SimpleRecordWithPreviousValue])
    def execute(@ParamName("keyBy") keyBy: LazyInterpreter[String],
               @ParamName("stringVal") stringVal: String) = FlinkCustomStreamTransformation((start: DataStream[InterpretationResult], ctx: FlinkCustomNodeContext) => {
      start.keyBy(keyBy.syncInterpretationFunction)
        .mapWithState[ValueWithContext[Any], Long] {
        //TODO: tu musi byc jakis node id??
        //na razie zawsze wszystko zwracamy..
        case (SimpleFromIr(ir, sr), Some(oldState)) =>
          (ValueWithContext(
          SimpleRecordWithPreviousValue(sr, oldState, stringVal), ir.finalContext), Some(sr.value1))
        case (SimpleFromIr(ir, sr), None) =>
          (ValueWithContext(
           SimpleRecordWithPreviousValue(sr, 0, stringVal), ir.finalContext), Some(sr.value1))
      }.map(CustomMap(ctx.exceptionHandler))


    })

    object SimpleFromIr {
      def unapply(ir:InterpretationResult) = Some((ir, ir.finalContext.apply[SimpleRecord]("input")))
    }

  }

  object CustomFilter extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("input") keyBy: LazyInterpreter[String],
               @ParamName("stringVal") stringVal: String) = FlinkCustomStreamTransformation((start: DataStream[InterpretationResult], context: FlinkCustomNodeContext) => {

      start.filter { ir =>
        keyBy.syncInterpretationFunction(ir) == stringVal
      }.map(ValueWithContext(_))
    })
  }

  object CustomContextClear extends CustomStreamTransformer {

    override val clearsContext = true

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("value") value: LazyInterpreter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[InterpretationResult], context: FlinkCustomNodeContext) => {
        start
          .map(ir => value.syncInterpretationFunction(ir))
          .map(ValueWithContext(_, Context("new")))
    })

  }

  case class CustomMap(lazyHandler: ()=>FlinkEspExceptionHandler) extends RichMapFunction[ValueWithContext[Any], ValueWithContext[Any]] with WithExceptionHandler {
    override def map(value: ValueWithContext[Any]) = {
       //tu nic madrego nie robimy, tylko zeby zobaczyc czy Exceptionhandler jest wstrzykniety
      try {
        value
      } catch {
        case e:Exception => exceptionHandler.handle(EspExceptionInfo(None, e, value.context))
          value
      }
    }
  }

  object MockService extends WithDataList[Any]

  //data is static, to be able to track, Service is object, to initialize metrics properly...
  class MockService extends Service with TimeMeasuringService {

    val serviceName = "mockService"

    @MethodToInvoke
    def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext) = {
      measuring(Future.successful {
        MockService.add(all)
      })
    }
  }

  case object MonitorEmptySink extends FlinkSink {
    val invocationsCount = new AtomicInteger(0)

    def clear() : Unit = {
      invocationsCount.set(0)
    }
    override def testDataOutput: Option[(Any) => String] = Some(output => output.toString)
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        invocationsCount.getAndIncrement()
      }
    }
  }

  case object SinkForInts extends FlinkSink with Serializable with WithDataList[Int] {

    override def toFlinkFunction = new SinkFunction[Any] {
      override def invoke(value: Any) = {
        add(value.toString.toInt)
      }
    }

    //stupid but have to make an error :|
    override def testDataOutput : Option[Any => String] = Some(_.toString.toInt.toString)
  }

  object EmptyService extends Service {
    def invoke() = Future.successful(Unit)
  }

}

object ProcessHelper {
  val constant = 4
  def add(a: Int, b: Int) : Int = {
    a + b
  }
}

trait WithDataList[T] {

  private val dataList = new CopyOnWriteArrayList[T]

  def add(element: T) : Unit = dataList.add(element)

  def data : List[T] = {
    dataList.toArray.toList.map(_.asInstanceOf[T])
  }

  def clear() : Unit = {
    dataList.clear()
  }
}
