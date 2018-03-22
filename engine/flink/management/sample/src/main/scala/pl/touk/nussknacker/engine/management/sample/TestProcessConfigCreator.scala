package pl.touk.nussknacker.engine.management.sample

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import argonaut.{Argonaut, EncodeJson, Json}
import com.typesafe.config.Config
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import org.apache.flink.api.common.serialization.SimpleStringSchema
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.lazyy.UsingLazyValues
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestParsingUtils}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory, KafkaSourceFactory}
import pl.touk.nussknacker.engine.management.sample.signal.{RemoveLockProcessSignalFactory, SampleSignalHandlingTransformer}

import scala.concurrent.{ExecutionContext, Future}
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ServiceInvocationCollector, TransmissionNames}
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink

class TestProcessConfigCreator extends ProcessConfigCreator {


  override def sinkFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None, None)

    val sendSmsSink = EmptySink
    val monitorSink = EmptySink
    Map(
      "sendSms" -> WithCategories(SinkFactory.noParam(sendSmsSink), "Category1"),
      "monitor" -> WithCategories(SinkFactory.noParam(monitorSink), "Category1", "Category2"),
      "kafka-string" -> WithCategories(new KafkaSinkFactory(kConfig,
        new KeyedSerializationSchema[Any] {
          override def serializeValue(element: Any) = element.toString.getBytes(StandardCharsets.UTF_8)

          override def serializeKey(element: Any) = null

          override def getTargetTopic(element: Any) = null
        }), "Category1", "Category2")
    )
  }

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None, None)

    Map(
      "real-kafka" -> WithCategories(new KafkaSourceFactory[String](kConfig,
        new SimpleStringSchema, None, TestParsingUtils.newLineSplit), "Category1", "Category2"),
      "kafka-transaction" -> WithCategories(FlinkSourceFactory.noParam(prepareNotEndingSource, Some(new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      })), "Category1", "Category2"),
      "oneSource" -> WithCategories(FlinkSourceFactory.noParam(new FlinkSource[String] {

        override def timestampAssigner = None

        override def toFlinkSource = new SourceFunction[String] {

          var run = true

          var emited = false

          override def cancel() = {
            run = false
          }

          override def run(ctx: SourceContext[String]) = {
            while (run) {
              if (!emited) ctx.collect("One element")
              emited = true
              Thread.sleep(1000)
            }
          }
        }

        override def typeInformation = implicitly[TypeInformation[String]]
      }), "Category1", "Category2"),
      "csv-source" -> WithCategories(FlinkSourceFactory.noParam(new FlinkSource[CsvRecord] with TestDataGenerator {
        override def typeInformation = implicitly[TypeInformation[CsvRecord]]

        override def toFlinkSource = new SourceFunction[CsvRecord] {
          override def cancel() = {}

          override def run(ctx: SourceContext[CsvRecord]) = {}

        }

        override def generateTestData(size: Int) = "record1|field2\nrecord2|field3".getBytes(StandardCharsets.UTF_8)

        override def timestampAssigner = None

      }, Some(new NewLineSplittedTestDataParser[CsvRecord] {
        override def parseElement(testElement: String): CsvRecord = CsvRecord(testElement.split("\\|").toList)
      })), "Category1", "Category2")
    )

  }


  //this not ending source is more reliable in tests than CollectionSource, which terminates quickly
  def prepareNotEndingSource: FlinkSource[String] = {
    new FlinkSource[String] {
      override def typeInformation = implicitly[TypeInformation[String]]

      override def timestampAssigner = Option(new BoundedOutOfOrdernessTimestampExtractor[String](Time.minutes(10)) {
        override def extractTimestamp(element: String): Long = System.currentTimeMillis()
      })

      override def toFlinkSource = new SourceFunction[String] {
        var running = true
        var counter = new AtomicLong()
        val afterFirstRun = new AtomicBoolean(false)

        override def cancel() = {
          running = false
        }

        override def run(ctx: SourceContext[String]) = {
          val r = new scala.util.Random
          while (running) {
            if (afterFirstRun.getAndSet(true)) {
              ctx.collect("TestInput" + r.nextInt(10))
            } else {
              ctx.collect("TestInput1")
            }
            Thread.sleep(2000)
          }
        }
      }
    }
  }

  override def services(config: Config) = {
    Map(
      "accountService" -> WithCategories(EmptyService, "Category1"),
      "componentService" -> WithCategories(EmptyService, "Category1", "Category2"),
      "transactionService" -> WithCategories(EmptyService, "Category1"),
      "serviceModelService" -> WithCategories(EmptyService, "Category1", "Category2"),
      "paramService" -> WithCategories(OneParamService, "Category1"),
      "enricher" -> WithCategories(Enricher, "Category1", "Category2"),
      "multipleParamsService" -> WithCategories(MultipleParamsService, "Category1", "Category2"),
      "complexReturnObjectService" -> WithCategories(ComplexReturnObjectService, "Category1", "Category2"),
      "listReturnObjectService" -> WithCategories(ListReturnObjectService, "Category1", "Category2"),
      "clientHttpService" -> WithCategories(new ClientFakeHttpService(), "Category1", "Category2")
    )
  }

  override def customStreamTransformers(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None, None)
    val signalsTopic = config.getString("signals.topic")
    Map(
      "stateful" -> WithCategories(StatefulTransformer, "Category1", "Category2"),
      "customFilter" -> WithCategories(CustomFilter, "Category1", "Category2"),
      "constantStateTransformer" -> WithCategories(ConstantStateTransformer[String](ConstantState("stateId", 1234, List("elem1", "elem2", "elem3")).asJson.nospaces), "Category1", "Category2"),
      "constantStateTransformerLongValue" -> WithCategories(ConstantStateTransformer[Long](12333), "Category1", "Category2"),

      "lockStreamTransformer" -> WithCategories(new SampleSignalHandlingTransformer.LockStreamTransformer(), "Category1", "Category2")
    )
  }

  override def signals(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None, None)
    val signalsTopic = config.getString("signals.topic")
    Map(
      "removeLockSignal" -> WithCategories(new RemoveLockProcessSignalFactory(kConfig, signalsTopic), "Category1", "Category2")
    )
  }

  override def exceptionHandlerFactory(config: Config) = ParamExceptionHandler

  override def expressionConfig(config: Config) = {
    val globalProcessVariables = Map(
      "DATE" -> WithCategories(DateProcessHelper, "Category1", "Category2")
    )
    ExpressionConfig(globalProcessVariables, List.empty)
  }

  override def buildInfo(): Map[String, String] = {
    Map(
      "process-version" -> "0.1",
      "engine-version" -> "0.1"
    )
  }
}

case object StatefulTransformer extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(@ParamName("keyBy") keyBy: LazyInterpreter[String])
  = FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) => {
    start.keyBy(keyBy.syncInterpretationFunction)
      .mapWithState[ValueWithContext[Any], List[String]] { case (StringFromIr(ir, sr), oldState) =>
      val nList = sr :: oldState.getOrElse(Nil)
      (ValueWithContext(nList, ir.finalContext), Some(nList))
    }
  })

  object StringFromIr {
    def unapply(ir: InterpretationResult) = Some(ir, ir.finalContext.apply[String]("input"))
  }

}

case class ConstantStateTransformer[T:TypeInformation](defaultValue: T) extends CustomStreamTransformer {


  final val stateName = "constantState"

  @MethodToInvoke
  @QueryableStateNames(values = Array(stateName))
  def execute() = FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) => {
    start
      .keyBy(_ => "1")
      .map(new RichMapFunction[InterpretationResult, ValueWithContext[Any]] {

        var constantState: ValueState[T] = _

        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          val descriptor = new ValueStateDescriptor[T]("constantState", implicitly[TypeInformation[T]])
          descriptor.setQueryable(stateName)
          constantState = getRuntimeContext.getState(descriptor)
        }

        override def map(value: InterpretationResult): ValueWithContext[Any] = {
          constantState.update(defaultValue)
          ValueWithContext[Any](value, value.finalContext)
        }
      }).uid("customStateId")
  })
}

case object CustomFilter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(@ParamName("expression") expression: LazyInterpreter[Boolean])
   = FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) =>
      start.filter(expression.syncInterpretationFunction).map(ValueWithContext(_)))

}

case object ParamExceptionHandler extends ExceptionHandlerFactory {
  @MethodToInvoke
  def create(@ParamName("param1") param: String, metaData: MetaData): EspExceptionHandler = VerboselyLoggingExceptionHandler(metaData)

}


case object EmptyService extends Service {
  @MethodToInvoke
  def invoke() = Future.successful(Unit)
}

case object OneParamService extends Service {
  @MethodToInvoke
  def invoke(@PossibleValues(value = Array("a", "b", "c")) @ParamName("param") param: String) = Future.successful(param)
}

case object Enricher extends Service {
  @MethodToInvoke
  def invoke(@ParamName("param") param: String) = Future.successful(RichObject(param, 123L, Some("rrrr")))
}

case class RichObject(field1: String, field2: Long, field3: Option[String])

case class CsvRecord(fields: List[String]) extends UsingLazyValues with Displayable {

  lazy val firstField = fields.head

  lazy val enrichedField = lazyValue[RichObject]("enricher", "param" -> firstField)

  override def display = Argonaut.jObjectFields("firstField" -> Json.jString(firstField))

  override def originalDisplay: Option[String] = Some(fields.mkString("|"))
}

case object ComplexReturnObjectService extends Service {
  @MethodToInvoke
  def invoke() = {
    Future.successful(ComplexObject(Map("foo" -> 1, "bar" -> "baz")))
  }
}

case object ListReturnObjectService extends Service {

  @MethodToInvoke
  def invoke() : Future[java.util.List[RichObject]] = {
    Future.successful(util.Arrays.asList(RichObject("abcd1", 1234L, Some("defg"))))
  }

}

case class Client(id: String, name: String) extends DisplayableAsJson[Client]

class ClientFakeHttpService() extends Service {
  import argonaut.ArgonautShapeless._

  case class LogClientRequest(method: String, id: String) extends DisplayableAsJson[LogClientRequest]
  case class LogClientResponse(body: String) extends DisplayableAsJson[LogClientResponse]

  @MethodToInvoke
  def invoke(@ParamName("id") id: String)(implicit executionContext: ExecutionContext, collector: ServiceInvocationCollector): Future[Client] = {
    val req = LogClientRequest("GET", id)
    collector.collectWithResponse(req, None) ({
      val client = Client(id, "foo")
      Future.successful(CollectableAction(() => LogClientResponse(client.asJson.spaces2), client))
    }, TransmissionNames("request", "response"))
  }
}


object ComplexObject {
  private implicit val mapEncoder = EncodeJson.of[Map[String, Json]]
    .contramap[Map[String, Any]](_.mapValues {
    case null => jNull
    case a:String => jString(a)
    case a:Long => jNumber(a)
    case a:Int => jNumber(a)
    case a:BigDecimal => jNumber(a)
    case a:Double => jNumber(a)
    case a:Boolean => jBool(a)
    case a => jString(a.toString)
  })

  val complexObjectEncoder = EncodeJson.of[ComplexObject]
}

case class ComplexObject(foo: Map[String, Any]) extends Displayable {
  override def display: Json = ComplexObject.complexObjectEncoder(this)
  override def originalDisplay: Option[String] = None
}

case object MultipleParamsService extends Service {
  @MethodToInvoke
  def invoke(@ParamName("foo") foo: String,
             @ParamName("bar") bar: String,
             @ParamName("baz") baz: String,
             @ParamName("quax") quax: String) = Future.successful(Unit)
}

object DateProcessHelper {
  @Documentation(
    description = "Returns current time in milliseconds." +
      "\nNote that while the unit of time of the return value is a millisecond," +
      "\nthe granularity of the value depends on the underlying" +
      "\noperating system and may be larger. For example, many" +
      "\noperating systems measure time in units of tens of milliseconds."
  )
  def nowTimestamp(): Long = System.currentTimeMillis()

  @Documentation(description = "Just parses a date.\n" +
    "Lorem ipsum dolor sit amet enim. Etiam ullamcorper. Suspendisse a pellentesque dui, non felis. Maecenas malesuada elit lectus felis, malesuada ultricies. Curabitur et ligula")
  def parseDate(@ParamName("dateString") dateString: String): LocalDateTime = {
    LocalDateTime.parse(dateString)
  }

  def noDocsMethod(date: Any, format: String): String = {
    ""
  }

  def paramsOnlyMethod(@ParamName("number") number: Int, @ParamName("format") format: String): String = {
    ""
  }

}

case class ConstantState(id: String, transactionId: Int, elements: List[String])
