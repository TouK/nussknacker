package pl.touk.nussknacker.engine.management.sample

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.circe.{Encoder, Json}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition.{Parameter, ServiceWithExplicitMethod}
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.static.StaticDictDefinition
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.lazyy.UsingLazyValues
import pl.touk.nussknacker.engine.api.process.{TestDataGenerator, _}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ServiceInvocationCollector, TransmissionNames}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser, TestParsingUtils}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.{TransformStateTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory, KafkaSourceFactory}
import pl.touk.nussknacker.engine.management.sample.signal.{RemoveLockProcessSignalFactory, SampleSignalHandlingTransformer}
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.sample.JavaSampleEnum

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object TestProcessConfigCreator {

  val oneElementValue = "One element"

}

class TestProcessConfigCreator extends ProcessConfigCreator {

  override def sinkFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)

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

  override def listeners(config: Config) = List(LoggingListener)

  override def sourceFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)

    Map(
      "real-kafka" -> WithCategories(new KafkaSourceFactory[String](kConfig,
        new SimpleStringSchema, None, TestParsingUtils.newLineSplit), "Category1", "Category2"),
      "kafka-transaction" -> WithCategories(FlinkSourceFactory.noParam(prepareNotEndingSource), "Category1", "Category2"),
      "boundedSource" -> WithCategories(BoundedSource, "Category1", "Category2"),
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
              if (!emited) ctx.collect(TestProcessConfigCreator.oneElementValue)
              emited = true
              Thread.sleep(1000)
            }
          }
        }

        override def typeInformation = implicitly[TypeInformation[String]]
      }), "Category1", "Category2"),
      "csv-source" -> WithCategories(FlinkSourceFactory.noParam(new FlinkSource[CsvRecord]
        with TestDataParserProvider[CsvRecord] with TestDataGenerator {

        override def typeInformation = implicitly[TypeInformation[CsvRecord]]

        override def toFlinkSource = new SourceFunction[CsvRecord] {
          override def cancel() = {}

          override def run(ctx: SourceContext[CsvRecord]) = {}

        }

        override def generateTestData(size: Int) = "record1|field2\nrecord2|field3".getBytes(StandardCharsets.UTF_8)

        override def testDataParser: TestDataParser[CsvRecord] = new NewLineSplittedTestDataParser[CsvRecord] {
          override def parseElement(testElement: String): CsvRecord = CsvRecord(testElement.split("\\|").toList)
        }

        override def timestampAssigner = None

      }), "Category1", "Category2")
    )

  }


  //this not ending source is more reliable in tests than CollectionSource, which terminates quickly
  def prepareNotEndingSource: FlinkSource[String] = {
    new FlinkSource[String] with TestDataParserProvider[String] {
      override def typeInformation = implicitly[TypeInformation[String]]

      override def timestampAssigner = Option(new BoundedOutOfOrdernessTimestampExtractor[String](Time.minutes(10)) {
        override def extractTimestamp(element: String): Long = System.currentTimeMillis()
      })

      override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      }

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
      "accountService" -> WithCategories(EmptyService, "Category1").withNodeConfig(SingleNodeConfig.zero.copy(docsUrl = Some("accountServiceDocs"))),
      "componentService" -> WithCategories(EmptyService, "Category1", "Category2"),
      "transactionService" -> WithCategories(EmptyService, "Category1"),
      "serviceModelService" -> WithCategories(EmptyService, "Category1", "Category2"),
      "paramService" -> WithCategories(OneParamService, "Category1"),
      "enricher" -> WithCategories(Enricher, "Category1", "Category2"),
      "multipleParamsService" -> WithCategories(MultipleParamsService, "Category1", "Category2"),
      "complexReturnObjectService" -> WithCategories(ComplexReturnObjectService, "Category1", "Category2"),
      "unionReturnObjectService" -> WithCategories(UnionReturnObjectService, "Category1", "Category2"),
      "listReturnObjectService" -> WithCategories(ListReturnObjectService, "Category1", "Category2"),
      "clientHttpService" -> WithCategories(new ClientFakeHttpService(), "Category1", "Category2"),
      "echoEnumService" -> WithCategories(EchoEnumService, "Category1", "Category2")
    )
  }

  override def customStreamTransformers(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)
    val signalsTopic = config.getString("signals.topic")
    Map(
      "noneReturnTypeTransformer" -> WithCategories(NoneReturnTypeTransformer, "TESTCAT"),
      "stateful" -> WithCategories(StatefulTransformer, "Category1", "Category2"),
      "customFilter" -> WithCategories(CustomFilter, "Category1", "Category2"),
      "constantStateTransformer" -> WithCategories(ConstantStateTransformer[String](Encoder[ConstantState].apply(ConstantState("stateId", 1234, List("elem1", "elem2", "elem3"))).noSpaces), "Category1", "Category2"),
      "constantStateTransformerLongValue" -> WithCategories(ConstantStateTransformer[Long](12333), "Category1", "Category2"),
      "additionalVariable" -> WithCategories(AdditionalVariableTransformer, "Category1", "Category2"),
      "lockStreamTransformer" -> WithCategories(new SampleSignalHandlingTransformer.LockStreamTransformer(), "Category1", "Category2"),
      "union" -> WithCategories(UnionTransformer, "Category1", "Category2"),
      "state" -> WithCategories(TransformStateTransformer, "Category1", "Category2")
    )
  }

  override def signals(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)
    val signalsTopic = config.getString("signals.topic")
    Map(
      "removeLockSignal" -> WithCategories(new RemoveLockProcessSignalFactory(kConfig, signalsTopic), "Category1", "Category2")
    )
  }

  override def exceptionHandlerFactory(config: Config) = ParamExceptionHandler

  override def expressionConfig(config: Config) = {
    val dictId = "dict"
    val dictDef = StaticDictDefinition(Map(
      "foo" -> "Foo",
      "bar" -> "Bar"))
    val globalProcessVariables = Map(
      "DATE" -> WithCategories(DateProcessHelper, "Category1", "Category2"),
      "DICT" -> WithCategories(DictInstance(dictId, dictDef), "Category1", "Category2"))
    ExpressionConfig(globalProcessVariables, List.empty, LanguageConfiguration(List()),
      dictionaries = Map(dictId -> WithCategories(dictDef, "Category1", "Category2")))
  }

  override def buildInfo(): Map[String, String] = {
    Map(
      "process-version" -> "0.1",
      "engine-version" -> "0.1"
    )
  }
}

object BoundedSource extends FlinkSourceFactory[Any] {

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, elements.asScala.toList, None, Unknown)

  override def timestampAssigner: Option[TimestampAssigner[Any]] = None
}

case object StatefulTransformer extends CustomStreamTransformer with LazyLogging {

  @MethodToInvoke
  def execute(@ParamName("keyBy") keyBy: LazyParameter[String])
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
    start
      .map(ctx.lazyParameterHelper.lazyMapFunction(keyBy))
      .keyBy(_.value)
      .mapWithState[ValueWithContext[Any], List[String]] { case (StringFromIr(ir, sr), oldState) =>
      logger.info(s"received: $sr, current state: $oldState")
      val nList = sr :: oldState.getOrElse(Nil)
      (ValueWithContext(nList, ir.context), Some(nList))
    }
  })

  object StringFromIr {
    def unapply(ir: ValueWithContext[_]) = Some(ir, ir.context.apply[String]("input"))
  }

}

case class ConstantStateTransformer[T:TypeInformation](defaultValue: T) extends CustomStreamTransformer {


  final val stateName = "constantState"

  @MethodToInvoke
  @QueryableStateNames(values = Array(stateName))
  def execute() = FlinkCustomStreamTransformation((start: DataStream[Context]) => {
    start
      .keyBy(_ => "1")
      .map(new RichMapFunction[Context, ValueWithContext[Any]] {

        var constantState: ValueState[T] = _

        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          val descriptor = new ValueStateDescriptor[T]("constantState", implicitly[TypeInformation[T]])
          descriptor.setQueryable(stateName)
          constantState = getRuntimeContext.getState(descriptor)
        }

        override def map(value: Context): ValueWithContext[Any] = {
          constantState.update(defaultValue)
          ValueWithContext[Any]("", value)
        }
      }).uid("customStateId")
  })
}

case object CustomFilter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(@ParamName("expression") expression: LazyParameter[Boolean])
   = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
      start
        .filter(ctx.lazyParameterHelper.lazyFilterFunction(expression))
        .map(ValueWithContext[Any](null, _)))

}


case object NoneReturnTypeTransformer extends CustomStreamTransformer {
  @MethodToInvoke(returnType = classOf[Void])
  def execute(@ParamName("expression") expression: LazyParameter[Boolean]) = {}
}


object AdditionalVariableTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(@AdditionalVariables(Array(new AdditionalVariable(name = "additional", clazz = classOf[String]))) @ParamName("expression") expression: LazyParameter[Boolean])
   = FlinkCustomStreamTransformation((start: DataStream[Context]) =>
      start.map(ValueWithContext[Any]("", _)))

}

case object ParamExceptionHandler extends ExceptionHandlerFactory {
  @MethodToInvoke
  def create(@ParamName("param1") param: String, metaData: MetaData): EspExceptionHandler = BrieflyLoggingExceptionHandler(metaData)

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
  def invoke(@ParamName("param") param: String, @ParamName("tariffType") tariffType: TariffType) = Future.successful(RichObject(param, 123L, Some("rrrr")))
}

case class RichObject(field1: String, field2: Long, field3: Option[String])

case class CsvRecord(fields: List[String]) extends UsingLazyValues with DisplayJson {

  lazy val firstField = fields.head

  lazy val enrichedField = lazyValue[RichObject]("enricher", "param" -> firstField)

  override def asJson: Json = Json.obj("firstField" -> Json.fromString(firstField))

  override def originalDisplay: Option[String] = Some(fields.mkString("|"))
}

case object ComplexReturnObjectService extends Service {
  @MethodToInvoke
  def invoke() = {
    Future.successful(ComplexObject(Map("foo" -> 1, "bar" -> "baz")))
  }
}

case object UnionReturnObjectService extends ServiceWithExplicitMethod {

  override def invokeService(params: List[AnyRef])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, metaData: MetaData): Future[AnyRef] =
    Future.successful(Map("foo" -> 1))

  override def parameterDefinition: List[Parameter] = List.empty

  override def returnType: typing.TypingResult = Typed(
    TypedObjectTypingResult(Map("foo" -> Typed[Int])),
    TypedObjectTypingResult(Map("bar" -> Typed[Int])))

}

case object ListReturnObjectService extends Service {

  @MethodToInvoke
  def invoke() : Future[java.util.List[RichObject]] = {
    Future.successful(util.Arrays.asList(RichObject("abcd1", 1234L, Some("defg"))))
  }

}

@JsonCodec case class Client(id: String, name: String) extends DisplayJsonWithEncoder[Client]

class ClientFakeHttpService() extends Service {

  @JsonCodec case class LogClientRequest(method: String, id: String) extends DisplayJsonWithEncoder[LogClientRequest]
  @JsonCodec case class LogClientResponse(body: String) extends DisplayJsonWithEncoder[LogClientResponse]

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

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false)

  private implicit val mapEncoder: Encoder[Map[String, Any]] = Encoder.instance[Map[String, Any]](encoder.encode)
}

@JsonCodec(encodeOnly = true) case class ComplexObject(foo: Map[String, Any]) extends DisplayJsonWithEncoder[ComplexObject]

case object MultipleParamsService extends Service {
  @MethodToInvoke
  def invoke(@ParamName("foo") foo: String,
             @ParamName("bar") bar: String,
             @ParamName("baz") baz: String,
             @ParamName("quax") quax: String) = Future.successful(Unit)
}

object DateProcessHelper {
  @Documentation(
    description = "Returns current time in milliseconds"
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

object EchoEnumService extends Service {
  @MethodToInvoke
  def invoke(@ParamName("id") id: JavaSampleEnum) = Future.successful(id)
}

@JsonCodec case class ConstantState(id: String, transactionId: Int, elements: List[String])
