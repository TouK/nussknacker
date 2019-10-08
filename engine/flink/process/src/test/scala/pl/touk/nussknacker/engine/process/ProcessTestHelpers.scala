package pl.touk.nussknacker.engine.process

import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import cats.data.Validated.Valid
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions.{FilterFunction, RuntimeContext}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.co.RichCoMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, ExceptionHandlerFactory, NonTransientException}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.typed.dict.TypedDictInstance
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.{LazyParameter, _}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.exception._
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.SimpleEnum
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler

import scala.concurrent.{ExecutionContext, Future}

object ProcessTestHelpers {

  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date, value3Opt: Option[BigDecimal] = None,
                          value3: BigDecimal = 1, intAsAny: Any = 1, enumValue: SimpleEnum.Value = SimpleEnum.One)

  case class SimpleRecordWithPreviousValue(record: SimpleRecord, previous: Long, added: String)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  object SimpleEnum extends Enumeration {
    // we must explicitly define Value class to recognize if type is matching
    class Value(name: String) extends Val(name)

    val One: Value = new Value("one")
    val Two: Value = new Value("two")
  }

  @JsonCodec case class SimpleJsonRecord(id: String, field: String)

  object processInvoker {

    def invoke(process: EspProcess, data: List[SimpleRecord],
               processVersion: ProcessVersion=ProcessVersion.empty,parallelism: Int = 1, config: Configuration = new Configuration()) = {
      FlinkTestConfiguration.addQueryableStatePortRanges(config)
      val env = StreamExecutionEnvironment.createLocalEnvironment(parallelism, config)
      val creator = prepareCreator(env.getConfig, data)
      env.getConfig.disableSysoutLogging

      new StandardFlinkProcessCompiler(creator, ConfigFactory.load()).createFlinkProcessRegistrar().register(env, process, processVersion)

      MockService.clear()
      env.execute(process.id)

    }

    def prepareCreator(exConfig: ExecutionConfig, data: List[SimpleRecord]) = new ProcessConfigCreator {

      override def services(config: Config) = Map(
        "logService" -> WithCategories(new MockService),
        "enricherWithOpenService" -> WithCategories(new EnricherWithOpenService)
      )

      override def sourceFactories(config: Config) = Map(
        "input" -> WithCategories(FlinkSourceFactory.noParam(new CollectionSource[SimpleRecord](
          config = exConfig,
          list = data,
          timestampAssigner = Some(new AscendingTimestampExtractor[SimpleRecord] {
            override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
          }), Typed[SimpleRecord]
        ))),
        "intInputWithParam" -> WithCategories(new IntParamSourceFactory(exConfig))
      )

      override def sinkFactories(config: Config) = Map(
        "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink)),
        "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts)),
        "sinkForStrings" -> WithCategories(SinkFactory.noParam(SinkForStrings))
      )

      override def customStreamTransformers(config: Config) = Map(
        "stateCustom" -> WithCategories(StateCustomNode),
        "customFilter" -> WithCategories(CustomFilter),
        "customFilterContextTransformation" -> WithCategories(CustomFilterContextTransformation),
        "customContextClear" -> WithCategories(CustomContextClear),
        "sampleJoin" -> WithCategories(CustomJoin),
        "joinBranchExpression" -> WithCategories(CustomJoinUsingBranchExpressions)
      )

      override def listeners(config: Config) = List()

      override def exceptionHandlerFactory(config: Config) =
        ExceptionHandlerFactory.noParams(RecordingExceptionHandler(_))


      override def expressionConfig(config: Config) = {
        val globalProcessVariables = Map(
          "processHelper" -> WithCategories(ProcessHelper),
          "enum" -> WithCategories(TypedDictInstance.forEnum[SimpleEnum.type](SimpleEnum).withValueClass[SimpleEnum.Value]))
        ExpressionConfig(globalProcessVariables, List.empty)
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

  class IntParamSourceFactory(exConfig: ExecutionConfig) extends FlinkSourceFactory[Int] {


    @MethodToInvoke
    def create(@ParamName("param") param: Int) = new CollectionSource[Int](config = exConfig,
      list = List(param),
      timestampAssigner = None, returnType = Typed[Int])

    override def timestampAssigner: Option[TimestampAssigner[Int]] = None

  }

  object RecordingExceptionHandler extends WithDataList[EspExceptionInfo[_ <: Throwable]]

  object StateCustomNode extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[SimpleRecordWithPreviousValue])
    def execute(@ParamName("stringVal") stringVal: String,
                @ParamName("keyBy") keyBy: LazyParameter[String]) = FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
      start
        .map(context.lazyParameterHelper.lazyMapFunction(keyBy))
        .keyBy(_.value)
        .mapWithState[ValueWithContext[Any], Long] {
        case (SimpleFromValueWithContext(ctx, sr), Some(oldState)) =>
          (ValueWithContext(
          SimpleRecordWithPreviousValue(sr, oldState, stringVal), ctx), Some(sr.value1))
        case (SimpleFromValueWithContext(ctx, sr), None) =>
          (ValueWithContext(
           SimpleRecordWithPreviousValue(sr, 0, stringVal), ctx), Some(sr.value1))
      }

    })

    object SimpleFromValueWithContext {
      def unapply(vwc:ValueWithContext[_]) = Some((vwc.context, vwc.context.apply[SimpleRecord]("input")))
    }

  }

  object CustomFilter extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("input") keyBy: LazyParameter[String],
                @ParamName("stringVal") stringVal: String) = FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {

      start
        .filter(new AbstractOneParamLazyParameterFunction(keyBy, context.lazyParameterHelper) with FilterFunction[Context] {
          override def filter(value: Context): Boolean = evaluateParameter(value) == stringVal
        })
        .map(ValueWithContext(null, _))
    })
  }


  object CustomFilterContextTransformation extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("input") keyBy: LazyParameter[String],
                @ParamName("stringVal") stringVal: String) =
      ContextTransformation
        .definedBy(Valid(_))
        .implementedBy(
          FlinkCustomStreamTransformation{ (start: DataStream[Context], context: FlinkCustomNodeContext) =>
            start
              .filter(new AbstractOneParamLazyParameterFunction(keyBy, context.lazyParameterHelper) with FilterFunction[Context] {
                override def filter(value: Context): Boolean = evaluateParameter(value) == stringVal
              })
              .map(ValueWithContext(null, _))
          })
  }


  object CustomContextClear extends CustomStreamTransformer {

    override val clearsContext = true

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("value") value: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        start
          .map(context.lazyParameterHelper.lazyMapFunction(value))
          .keyBy(_.value)
          .map(_ => ValueWithContext[Any](null, Context("new")))
    })

  }

  object CustomJoin extends CustomStreamTransformer {

    override val clearsContext = true

    @MethodToInvoke
    def execute(): FlinkCustomJoinTransformation =
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]] = {
          val inputFromIr = (ir:Context) => ValueWithContext(ir.variables("input"), ir)
          inputs("end1")
            .connect(inputs("end2"))
            .map(inputFromIr, inputFromIr)
        }
      }

  }

  object CustomJoinUsingBranchExpressions extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
                @OutputVariableName variableName: String): JoinContextTransformation =
      ContextTransformation
        .join.definedBy { contexts =>
        val newType = TypedObjectTypingResult(contexts.toSeq.map {
          case (branchId, _) =>
            branchId -> valueByBranchId(branchId).returnType
        }.toMap)
        Valid(ValidationContext(Map(variableName -> newType)))
      }.implementedBy(
        new FlinkCustomJoinTransformation {
          override def transform(inputs: Map[String, DataStream[Context]],
                                 flinkContext: FlinkCustomNodeContext): DataStream[ValueWithContext[Any]] = {
            inputs("end1")
              .connect(inputs("end2"))
              .map(new JoinExprBranchFunction(valueByBranchId, flinkContext.lazyParameterHelper))
          }
        })

  }

  class JoinExprBranchFunction(valueByBranchId: Map[String, LazyParameter[_]],
                               val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
    extends RichCoMapFunction[Context, Context, ValueWithContext[Any]] with LazyParameterInterpreterFunction {

    @transient lazy val end1Interpreter: Context => Any =
      lazyParameterInterpreter.syncInterpretationFunction(valueByBranchId("end1"))

    @transient lazy val end2Interpreter: Context => Any =
      lazyParameterInterpreter.syncInterpretationFunction(valueByBranchId("end2"))

    override def map1(ctx: Context): ValueWithContext[Any] = {
      ValueWithContext(end1Interpreter(ctx), ctx)
    }

    override def map2(ctx: Context): ValueWithContext[Any] = {
      ValueWithContext(end2Interpreter(ctx), ctx)
    }

  }

  object MockService extends WithDataList[Any]

  //data is static, to be able to track, Service is object, to initialize metrics properly...
  class MockService extends Service with TimeMeasuringService {

    val serviceName = "mockService"

    @MethodToInvoke
    def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext): Future[Unit] = {
      measuring(Future.successful {
        MockService.add(all)
      })
    }
  }

  class EnricherWithOpenService extends Service with TimeMeasuringService {

    val serviceName = "enricherWithOpenService"

    var internalVar: String = _

    override def open(jobData: JobData): Unit = {
      super.open(jobData)
      internalVar = "initialized!"
    }

    @MethodToInvoke
    def invoke()(implicit ec: ExecutionContext): Future[String] = {
      measuring(Future.successful {
        internalVar
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

  case object SinkForStrings extends FlinkSink with Serializable with WithDataList[String] {
    override def toFlinkFunction = new SinkFunction[Any] {
      override def invoke(value: Any) = {
        add(value.toString)
      }
    }
    override def testDataOutput : Option[Any => String] = None
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
