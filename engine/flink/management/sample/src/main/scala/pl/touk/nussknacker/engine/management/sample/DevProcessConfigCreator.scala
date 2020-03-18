package pl.touk.nussknacker.engine.management.sample

import java.io.File
import java.nio.charset.StandardCharsets
import java.time._
import java.time.temporal.ChronoUnit
import java.util
import java.util.{Date, Optional}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.cronutils.model.Cron
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.circe.{Encoder, Json}
import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, MandatoryParameterValidator, Parameter, ServiceWithExplicitMethod, StringParameterEditor}
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, LabeledExpression, RawEditor, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.lazyy.UsingLazyValues
import pl.touk.nussknacker.engine.api.process.{TestDataGenerator, _}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{CollectableAction, ServiceInvocationCollector, TransmissionNames}
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser, TestParsingUtils}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.validation.Literal
import pl.touk.nussknacker.engine.api.{AdditionalVariable, AdditionalVariables, Context, CustomStreamTransformer, DisplayJson, DisplayJsonWithEncoder, Documentation, HideToString, LazyParameter, MetaData, MethodToInvoke, ParamName, QueryableStateNames, Service, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.aggregates.AggregateHelper
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SlidingAggregateTransformer
import pl.touk.nussknacker.engine.flink.util.transformer.{TransformStateTransformer, UnionTransformer}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory, KafkaSourceFactory}
import pl.touk.nussknacker.engine.management.sample.signal.{RemoveLockProcessSignalFactory, SampleSignalHandlingTransformer}
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.sample.JavaSampleEnum

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Properties

object DevProcessConfigCreator {

  val oneElementValue = "One element"

}

/**
 * This config creator is for purpose of development, for end-to-end tests
 */
class DevProcessConfigCreator extends ProcessConfigCreator {

  override def sinkFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)

    val sendSmsSink = EmptySink
    val monitorSink = EmptySink
    Map(
      "sendSms" -> all(SinkFactory.noParam(sendSmsSink)),
      "monitor" -> all(SinkFactory.noParam(monitorSink)),
      "kafka-string" -> all(new KafkaSinkFactory(kConfig, new SimpleSerializationSchema[Any](_, _.toString)))
    )
  }

  override def listeners(config: Config) = List(LoggingListener)

  override def sourceFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)

    Map(
      "real-kafka" -> all(new KafkaSourceFactory[String](kConfig,
        new SimpleStringSchema, None, TestParsingUtils.newLineSplit)),
      "kafka-transaction" -> all(FlinkSourceFactory.noParam(prepareNotEndingSource)),
      "boundedSource" -> all(BoundedSource),
      "oneSource" -> all(FlinkSourceFactory.noParam(new BasicFlinkSource[String] {

        override def timestampAssigner = None

        override def flinkSourceFunction = new SourceFunction[String] {

          var run = true

          var emited = false

          override def cancel() = {
            run = false
          }

          override def run(ctx: SourceContext[String]) = {
            while (run) {
              if (!emited) ctx.collect(DevProcessConfigCreator.oneElementValue)
              emited = true
              Thread.sleep(1000)
            }
          }
        }

        override val typeInformation: TypeInformation[String] = implicitly[TypeInformation[String]]
      })),
      "csv-source" -> all(FlinkSourceFactory.noParam(new BasicFlinkSource[CsvRecord]
        with TestDataParserProvider[CsvRecord] with TestDataGenerator {

        override val typeInformation: TypeInformation[CsvRecord] = implicitly[TypeInformation[CsvRecord]]

        override def flinkSourceFunction = new SourceFunction[CsvRecord] {
          override def cancel() = {}

          override def run(ctx: SourceContext[CsvRecord]) = {}

        }

        override def generateTestData(size: Int) = "record1|field2\nrecord2|field3".getBytes(StandardCharsets.UTF_8)

        override def testDataParser: TestDataParser[CsvRecord] = new NewLineSplittedTestDataParser[CsvRecord] {
          override def parseElement(testElement: String): CsvRecord = CsvRecord(testElement.split("\\|").toList)
        }

        override def timestampAssigner = None

      }))
    )

  }


  //this not ending source is more reliable in tests than CollectionSource, which terminates quickly
  def prepareNotEndingSource: BasicFlinkSource[String] = {
    new BasicFlinkSource[String] with TestDataParserProvider[String] {
      override val typeInformation = implicitly[TypeInformation[String]]

      override def timestampAssigner = Option(new BoundedOutOfOrdernessTimestampExtractor[String](Time.minutes(10)) {
        override def extractTimestamp(element: String): Long = System.currentTimeMillis()
      })

      override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      }

      override def flinkSourceFunction = new SourceFunction[String] {
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
      "accountService" -> all(EmptyService).withNodeConfig(SingleNodeConfig.zero.copy(docsUrl = Some("accountServiceDocs"))),
      "componentService" -> all(EmptyService),
      "transactionService" -> all(EmptyService),
      "serviceModelService" -> all(EmptyService),
      "paramService" -> all(OneParamService),
      "enricher" -> all(Enricher),
      "multipleParamsService" -> all(MultipleParamsService)
        .withNodeConfig(SingleNodeConfig.zero.copy(
          params = Some(Map(
            "foo" -> ParameterConfig(None, Some(FixedValuesParameterEditor(List(FixedExpressionValue("test", "test")))), None, None),
            "bar" -> ParameterConfig(None, Some(StringParameterEditor), None, None),
            "baz" -> ParameterConfig(None, Some(StringParameterEditor), None, None)
          )))
        ),
      "complexReturnObjectService" -> all(ComplexReturnObjectService),
      "unionReturnObjectService" -> all(UnionReturnObjectService),
      "listReturnObjectService" -> all(ListReturnObjectService),
      "clientHttpService" -> all(new ClientFakeHttpService()),
      "echoEnumService" -> all(EchoEnumService),
      // types
      "simpleTypesService"  -> all(new SimpleTypesService).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("types"))),
      "optionalTypesService"  -> all(new OptionalTypesService)
        .withNodeConfig(SingleNodeConfig.zero.copy(
          category = Some("types"),
          params = Some(Map(
            "overriddenByDevConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None),
            "overriddenByFileConfigParam" -> ParameterConfig(None, None, Some(List(MandatoryParameterValidator)), None)
          ))
        )),
      "collectionTypesService"  -> all(new CollectionTypesService).withNodeConfig(SingleNodeConfig.zero.copy(
        category = Some("types"))),
      "datesTypesService"  -> all(new DatesTypesService).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("types"))),
      "dynamicService" -> all(new DynamicService)
    )
  }

  override def customStreamTransformers(config: Config) = {
    Map(
      "noneReturnTypeTransformer" -> WithCategories(NoneReturnTypeTransformer, "TESTCAT"),
      "stateful" -> all(StatefulTransformer),
      "customFilter" -> all(CustomFilter),
      "constantStateTransformer" -> all(ConstantStateTransformer[String](Encoder[ConstantState].apply(ConstantState("stateId", 1234, List("elem1", "elem2", "elem3"))).noSpaces)),
      "constantStateTransformerLongValue" -> all(ConstantStateTransformer[Long](12333)),
      "additionalVariable" -> all(AdditionalVariableTransformer),
      "lockStreamTransformer" -> all(new SampleSignalHandlingTransformer.LockStreamTransformer()),
      "aggregate" -> all(SlidingAggregateTransformer),
      "union" -> all(UnionTransformer),
      "state" -> all(TransformStateTransformer),
      // types
      "simpleTypesCustomNode" -> all(new SimpleTypesCustomStreamTransformer).withNodeConfig(SingleNodeConfig.zero.copy(category = Some("types")))
    )
  }

  override def signals(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.kafkaAddress"), None, None)
    val signalsTopic = config.getString("signals.topic")
    Map(
      "removeLockSignal" -> all(new RemoveLockProcessSignalFactory(kConfig, signalsTopic))
    )
  }

  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

  override def expressionConfig(config: Config) = {
    val dictId = "dict"
    val dictDef = EmbeddedDictDefinition(Map(
      "foo" -> "Foo",
      "bar" -> "Bar",
      "sentence-with-spaces-and-dots" -> "Sentence with spaces and . dots"))
    val globalProcessVariables = Map(
      "AGG" -> all(AggregateHelper),
      "DATE" -> all(DateProcessHelper),
      "DICT" -> all(DictInstance(dictId, dictDef)))
    ExpressionConfig(globalProcessVariables, List.empty, LanguageConfiguration(List()),
      dictionaries = Map(dictId -> all(dictDef)))
  }

  private def all[T](value: T) = WithCategories(value, "Category1", "Category2")

  //we generate static generation-time during ConfigCreator creation to test reload mechanisms
  override val buildInfo: Map[String, String] = {
    Map(
      "process-version" -> "0.1",
      "engine-version" -> "0.1",
      "generation-time" -> LocalDateTime.now().toString
    )
  }
}

object BoundedSource extends FlinkSourceFactory[Any] {

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, elements.asScala.toList, None, Unknown)

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
  def invoke(@SimpleEditor(
               `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
               possibleValues = Array(
                 new LabeledExpression(expression = "'a'", label = "a"),
                 new LabeledExpression(expression = "'b'", label = "b"),
                 new LabeledExpression(expression = "'c'", label = "c")
               )
             )
             @ParamName("param") param: String) = Future.successful(param)
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
             @ParamName("bar")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )
             bar: String,
             @ParamName("baz") baz: String,
             @ParamName("quax") quax: String) = Future.successful(Unit)
}

object DateProcessHelper extends HideToString {
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

// In custom stream by default all parameters are eagerly evaluated, you need to define type LazyParameter to make it lazy
class SimpleTypesCustomStreamTransformer extends CustomStreamTransformer with Serializable {
  @MethodToInvoke(returnType = classOf[Void])
  def invoke(@ParamName("booleanParam") booleanParam: Boolean,
             @ParamName("lazyBooleanParam") lazyBooleanParam: LazyParameter[Boolean],
             @ParamName("stringParam") string: String,
             @ParamName("intParam") intParam: Int,
             @ParamName("bigDecimalParam") bigDecimalParam: java.math.BigDecimal,
             @ParamName("bigIntegerParam") bigIntegerParam: java.math.BigInteger): Unit = {
  }
}

// In services all parameters are lazy evaluated
class SimpleTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("booleanParam")
             @SimpleEditor(
               `type` = SimpleEditorType.BOOL_EDITOR
             ) booleanParam: Boolean,

             @ParamName("DualParam")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.SIMPLE
             )
             @NotBlank
             dualParam: String,

             @ParamName("SimpleParam")
             @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
             simpleParam: String,

             @ParamName("RawParam")
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             rawParam: String,

             @ParamName("intParam")
             @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
             @Literal
             intParam: Int,

             @ParamName("rawIntParam")
             @RawEditor
             @Literal
             rawIntParam: Int,

             @ParamName("fixedValuesStringParam")
             @SimpleEditor(
               `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
               possibleValues = Array(
                 new LabeledExpression(expression = "'Max'", label = "Max"),
                 new LabeledExpression(expression = "'Min'", label = "Min")
               )
             ) fixedValuesStringParam: String,

             @ParamName("bigDecimalParam") bigDecimalParam: java.math.BigDecimal,
             @ParamName("bigIntegerParam") bigIntegerParam: java.math.BigInteger): Future[Unit] = {
    ???
  }
}

class OptionalTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("scalaOptionParam") scalaOptionParam: Option[Int],
             @ParamName("javaOptionalParam") javaOptionalParam: Optional[Int],
             @ParamName("nullableParam") @Nullable nullableParam: Int,
             @ParamName("dateTimeParam") @Nullable dateTimeParam: LocalDateTime,
             @ParamName("overriddenByDevConfigParam") overriddenByDevConfigParam: Option[String],
             @ParamName("overriddenByFileConfigParam") overriddenByFileConfigParam: Option[String]): Future[Unit] = {
    ???
  }
}

class CollectionTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("listParam") listParam: java.util.List[Int],
             @ParamName("mapParam") mapParam: java.util.Map[String, Int]): Future[Unit] = {
    ???
  }
}

class DatesTypesService extends Service with Serializable {
  @MethodToInvoke
  def invoke(@ParamName("dateTimeParam") dateTimeParam: LocalDateTime,
             @ParamName("dateParam") dateParam: LocalDate,
             @ParamName("timeParam") timeParam: LocalTime,
             @ParamName("zonedDataTimeParam") zonedDataTimeParam: ZonedDateTime,

             @ParamName("durationParam")
             @DualEditor(
               simpleEditor = new SimpleEditor(
                 `type` = SimpleEditorType.DURATION_EDITOR,
                 timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS)
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             duration: Duration,

             @ParamName("periodParam")
             @DualEditor(
               simpleEditor = new SimpleEditor(
                 `type` = SimpleEditorType.PERIOD_EDITOR,
                 timeRangeComponents = Array(ChronoUnit.YEARS, ChronoUnit.MONTHS)
               ),
               defaultMode = DualEditorMode.SIMPLE
             )
             period: Period,

             @ParamName("cronScheduleParam")
             @SimpleEditor(`type` = SimpleEditorType.CRON_EDITOR)
             cronScheduleParam: Cron
            ): Future[Unit] = {
    ???
  }
}

//this is to simulate model reloading - we read parameters from file
class DynamicService extends ServiceWithExplicitMethod {

  private val fileWithDefinition = new File(Properties.tmpDir, "nkDynamicServiceProperties")

  override def invokeService(params: List[AnyRef])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, metaData: MetaData): Future[AnyRef] = ???

  //we load parameters only *once* per service creation
  override val parameterDefinition: List[Parameter] = {
    val paramNames = if (fileWithDefinition.exists()) {
      FileUtils.readLines(fileWithDefinition).asScala.toList
    } else Nil
    paramNames.map(name => Parameter[String](name))
  }

  override def returnType: typing.TypingResult = Unknown
}