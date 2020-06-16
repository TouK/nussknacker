package pl.touk.nussknacker.engine.process.helpers

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Date, Optional, UUID}

import cats.data.Validated.Valid
import io.circe.generic.JsonCodec
import javax.annotation.Nullable
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions.FilterFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.co.RichCoMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.timestamps.{AscendingTimestampExtractor, BoundedOutOfOrdernessTimestampExtractor}
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.apache.flink.streaming.api.windowing.time.Time
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedParameter, FailedToDefineParameter, NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{EmptyLineSplittedTestDataParser, NewLineSplittedTestDataParser, TestDataParser, TestParsingUtils}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, ServiceReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.flink.util.signal.KafkaSignalStreamConnector
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EspDeserializationSchema}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.GenericParametersSource
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.engine.util.Implicits._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

//TODO: clean up sample objects...
object SampleNodes {

  val RecordingExceptionHandler = new CommonTestHelpers.RecordingExceptionHandler

  // Unfortunately we can't use scala Enumeration because of limited scala TypeInformation macro - see note in TypedDictInstance
  case class SimpleRecord(id: String, value1: Long, value2: String, date: Date, value3Opt: Option[BigDecimal] = None,
                          value3: BigDecimal = 1, intAsAny: Any = 1, enumValue: SimpleJavaEnum = SimpleJavaEnum.ONE)

  case class SimpleRecordWithPreviousValue(record: SimpleRecord, previous: Long, added: String)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  @JsonCodec case class SimpleJsonRecord(id: String, field: String)

  class IntParamSourceFactory(exConfig: ExecutionConfig) extends FlinkSourceFactory[Int] {

    @MethodToInvoke
    def create(@ParamName("param") param: Int) = new CollectionSource[Int](config = exConfig,
      list = List(param),
      timestampAssigner = None, returnType = Typed[Int])

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

  object ServiceAcceptingScalaOption extends Service {
    @MethodToInvoke
    def invoke(@ParamName("scalaOptionParam") scalaOptionParam: Option[String]): Future[Option[String]] = Future.successful(scalaOptionParam)
  }

  object StateCustomNode extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[SimpleRecordWithPreviousValue])
    def execute(@ParamName("stringVal") stringVal: String,
                @ParamName("keyBy") keyBy: LazyParameter[String]) = FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
      setUidToNodeIdIfNeed(context,
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
          })
    })

    object SimpleFromValueWithContext {
      def unapply(vwc: ValueWithContext[_]) = Some((vwc.context, vwc.context.apply[SimpleRecord]("input")))
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
        .map(ValueWithContext[Any](null, _))
    })
  }

  object CustomFilterContextTransformation extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("input") keyBy: LazyParameter[String], @ParamName("stringVal") stringVal: String): ContextTransformation =
      ContextTransformation
        .definedBy(Valid(_))
        .implementedBy(
          FlinkCustomStreamTransformation { (start: DataStream[Context], context: FlinkCustomNodeContext) =>
            start
              .filter(new AbstractOneParamLazyParameterFunction(keyBy, context.lazyParameterHelper) with FilterFunction[Context] {
                override def filter(value: Context): Boolean = evaluateParameter(value) == stringVal
              })
              .map(ValueWithContext[Any](null, _))
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
          val inputFromIr = (ir: Context) => ValueWithContext(ir.variables("input"), ir)
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
        val newType = Typed(contexts.keys.toList.map(branchId => valueByBranchId(branchId).returnType): _*)
        val parent = contexts.values.flatMap(_.parent).headOption
        Valid(ValidationContext(Map(variableName -> newType), Map.empty, parent))
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

  object ReturningDependentTypeService extends Service with ServiceReturningType {

    @MethodToInvoke
    def invoke(@ParamName("definition") definition: java.util.List[String],
               @ParamName("toFill") toFill: String, @ParamName("count") count: Int): Future[java.util.List[_]] = {
      val result = (1 to count)
        .map(line => definition.asScala.map(_ -> toFill).toMap)
        .map(TypedMap(_))
        .toList.asJava
      Future.successful(result)
    }

    //returns list of type defined by definition parameter
    override def returnType(parameters: Map[String, (TypingResult, Option[Any])]): typing.TypingResult = {
      parameters
        .get("definition")
        .flatMap(_._2)
        .map(definition => TypedObjectTypingResult(definition.asInstanceOf[java.util.List[String]].asScala.map(_ -> Typed[String]).toMap))
        .map(param => Typed.genericTypeClass[java.util.List[_]](List(param)))
        .getOrElse(Unknown)
    }
  }

  object LogService extends Service {

    val invocationsCount = new AtomicInteger(0)

    def clear(): Unit = {
      invocationsCount.set(0)
    }

    @MethodToInvoke
    def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
      collector.collect(s"$all-collectedDuringServiceInvocation", Option(())) {
        invocationsCount.incrementAndGet()
        Future.successful(())
      }
    }
  }

  class ThrowingService(exception: Exception) extends Service {
    @MethodToInvoke
    def invoke(@ParamName("throw") throwing: Boolean): Future[Unit] = {
      if (throwing) {
        Future.failed(exception)
      } else  Future.successful(Unit)
    }
  }


  object CustomSignalReader extends CustomStreamTransformer {

    @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
    @MethodToInvoke(returnType = classOf[Void])
    def execute() =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        context.signalSenderProvider.get[TestProcessSignalFactory]
          .connectWithSignals(start, context.metaData.id, context.nodeId, new EspDeserializationSchema(identity))
          .map((a:Context) => ValueWithContext("", a),
                (_:Array[Byte]) => ValueWithContext[Any]("", Context("id")))
    })
  }

  object TransformerWithTime extends CustomStreamTransformer {

    override def clearsContext = true

    @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
    @MethodToInvoke(returnType = classOf[Int])
    def execute(@ParamName("seconds") seconds: Int) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        start
          .map(_ => 1)
          .timeWindowAll(Time.seconds(seconds)).reduce(_ + _)
          .map(ValueWithContext[Any](_, Context(UUID.randomUUID().toString)))
      })
  }

  object TransformerWithNullableParam extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[String])
    def execute(@ParamName("param") @Nullable param: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        start
          .map(context.lazyParameterHelper.lazyMapFunction[Any](param))
      })

  }

  object OptionalEndingCustom extends CustomStreamTransformer {
    
    override def canBeEnding: Boolean = true

    @MethodToInvoke
    def execute(@ParamName("param") @Nullable param: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        val afterMap = start.map(context.lazyParameterHelper.lazyMapFunction[Any](param))
        afterMap.addSink(element => MockService.add(element.value))
        afterMap
      })

  }
  
  class TestProcessSignalFactory(val kafkaConfig: KafkaConfig, val signalsTopic: String)
    extends FlinkProcessSignalSender with KafkaSignalStreamConnector {

    @MethodToInvoke
    def sendSignal()(processId: String): Unit = {
      KafkaUtils.sendToKafkaWithTempProducer(signalsTopic, Array.empty[Byte], "".getBytes(StandardCharsets.UTF_8))(kafkaConfig)
    }

  }

  object LazyParameterSinkFactory extends SinkFactory {

    override def requiresOutput: Boolean = false

    @MethodToInvoke
    def createSink(@ParamName("intParam") value: LazyParameter[Int]): Sink = new FlinkSink {

      override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
        dataStream
          .map(_.finalContext)
          .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .map(_.value.asInstanceOf[Any])
          .addSink(SinkForInts.toFlinkFunction)
      }

      override def testDataOutput: Option[Any => String] = None
    }

  }

  object EagerOptionalParameterSinkFactory extends SinkFactory with WithDataList[String] {

    override def requiresOutput: Boolean = false

    @MethodToInvoke
    def createSink(@ParamName("optionalStringParam") value: Optional[String]): Sink = new FlinkSink {

      val sinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
        override def invoke(value: Any): Unit = {
          add(value.toString)
        }
      }

      override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
        val serializableValue = value.orElse(null) // Java's Optional is not serializable
        dataStream
          .map(_ => serializableValue: Any)
          .addSink(sinkFunction)
      }

      override def testDataOutput: Option[Any => String] = None
    }

  }

  object MockService extends Service with WithDataList[Any]

  case object MonitorEmptySink extends BasicFlinkSink {
    val invocationsCount = new AtomicInteger(0)

    def clear(): Unit = {
      invocationsCount.set(0)
    }

    override def testDataOutput: Option[Any => String] = Some(output => output.toString)

    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        invocationsCount.getAndIncrement()
      }
    }
  }

  case object SinkForInts extends BasicFlinkSink with WithDataList[Int] {

    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        add(value.toString.toInt)
      }
    }

    //stupid but have to make an error :|
    override def testDataOutput: Option[Any => String] = Some(_.toString.toInt.toString)
  }

  case object SinkForStrings extends BasicFlinkSink with WithDataList[String] {
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        add(value.toString)
      }
    }

    override def testDataOutput: Option[Any => String] = None
  }

  object EmptyService extends Service {
    def invoke(): Future[Unit.type] = Future.successful(Unit)
  }

  object GenericParametersNode extends CustomStreamTransformer with SingleInputGenericNodeTransformation[AnyRef] {

    override type State = List[String]

    override def contextTransformation(context: ValidationContext,
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("par1", DefinedEagerParameter(value: String, _))::("lazyPar1", _)::Nil, None) =>
        val split = value.split(",").toList
        NextParameters(split.map(Parameter(_, Unknown)), state = Some(split))
      case TransformationStep(("par1", FailedToDefineParameter)::("lazyPar1", _)::Nil, None) =>
        outputParameters(context, dependencies, Nil)
      case TransformationStep(("par1", _)::("lazyPar1", _)::rest, Some(names)) if rest.map(_._1) == names =>
        outputParameters(context, dependencies, rest)
    }

    private def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, DefinedParameter)])(implicit nodeId: NodeId): this.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          val result = TypedObjectTypingResult(rest.toMap.mapValuesNow(_.returnType))
          context.withVariable(name, result).fold(
            errors => FinalResults(context, errors.toList),
            FinalResults(_))
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def initialParameters: List[Parameter] = List(
      Parameter[String]("par1"), Parameter[Boolean]("lazyPar1").copy(isLazyParameter = true)
    )

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef = {
      val map = params.filterNot(k => List("par1", "lazyPar1").contains(k._1))
      val bool = params("lazyPar1").asInstanceOf[LazyParameter[Boolean]]
      FlinkCustomStreamTransformation((stream, fctx) => {
        stream
          .filter(new LazyParameterFilterFunction(bool, fctx.lazyParameterHelper))
          .map(ctx => ValueWithContext[Any](TypedMap(map), ctx))
      })
    }

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency(classOf[MetaData]))


  }

  object GenericParametersSource extends FlinkSourceFactory[AnyRef] with SingleInputGenericNodeTransformation[Source[AnyRef]] {

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId)
      : this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("type", DefinedEagerParameter(value: String, _))::Nil, None) =>
        val versions = value match {
          case "type1" => List(1, 2)
          case "type2" => List(3, 4)
          case _ => ???
        }
        NextParameters(Parameter[Int]("version")
              .copy(editor = Some(FixedValuesParameterEditor(versions.map(v => FixedExpressionValue(v.toString, v.toString))))):: Nil)
      case TransformationStep(("type", FailedToDefineParameter)::Nil, None) =>
        output(context, dependencies)
      case TransformationStep(("type", _)::("version", _)::Nil, None) =>
        output(context, dependencies)
    }

    private def output(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId) = {
      context.withVariable(dependencies.collectFirst {
        case OutputVariableNameValue(name) => name
      }.get, Typed[String]).fold(
        errors => FinalResults(context, errors.toList),
        FinalResults(_))
    }

    override def initialParameters: List[Parameter] = Parameter[String]("type")
      .copy(editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue("'type1'", "type1"), FixedExpressionValue("'type2'", "type2"))))) :: Nil

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef = {
      val out = params("type") + "-" + params("version")
      CollectionSource(StreamExecutionEnvironment.getExecutionEnvironment.getConfig, out::Nil, None, Typed[String])
    }

    override def nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: Nil
  }

  object GenericParametersSink extends SinkFactory with SingleInputGenericNodeTransformation[Sink]  {

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId)
      : this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("value", _) :: ("type", DefinedEagerParameter(value: String, _))::Nil, None) =>
        val versions = value match {
          case "type1" => List(1, 2)
          case "type2" => List(3, 4)
          case _ => ???
        }
        NextParameters(Parameter[Int]("version")
              .copy(editor = Some(FixedValuesParameterEditor(versions.map(v => FixedExpressionValue(v.toString, v.toString))))):: Nil)
      case TransformationStep(("value", _) :: ("type", FailedToDefineParameter)::Nil, None) => FinalResults(context)
      case TransformationStep(("value", _) :: ("type", _)::("version", _)::Nil, None) => FinalResults(context)
    }
    override def initialParameters: List[Parameter] = Parameter[String]("value").copy(isLazyParameter = true) :: Parameter[String]("type")
      .copy(editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue("'type1'", "type1"), FixedExpressionValue("'type2'", "type2"))))) :: Nil

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef = {
      new FlinkSink with Serializable {
        private val typ = params("type")
        private val version = params("version")

        override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
          dataStream
            .map(_.finalContext)
            .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(params("value").asInstanceOf[LazyParameter[String]]))
            .map[Any]((v: ValueWithContext[String]) => s"${v.value}+$typ-$version")
            .addSink(SinkForStrings.toFlinkFunction)
        }

        override def testDataOutput: Option[Any => String] = None
      }
    }

    override def nodeDependencies: List[NodeDependency] = Nil
  }


  object ProcessHelper {

    val constant = 4

    def add(a: Int, b: Int): Int =  a + b

    def scalaOptionValue: Option[String] = Some("" + constant)

    def javaOptionalValue: Optional[String] = Optional.of("" + constant)

    def extractProperty(map: java.util.Map[String, _], property: String): Any = map.get(property)

  }

  private val ascendingTimestampExtractor = new AscendingTimestampExtractor[SimpleRecord] {
    override def extractAscendingTimestamp(element: SimpleRecord): Long = element.date.getTime
  }

  private def outOfOrdernessTimestampExtractor[T](extract: T => Long) = new BoundedOutOfOrdernessTimestampExtractor[T](Time.minutes(10)) {
    override def extractTimestamp(element: T): Long = extract(element)
  }

  private val newLineSplittedTestDataParser = new NewLineSplittedTestDataParser[SimpleRecord] {
    override def parseElement(csv: String): SimpleRecord = {
      val parts = csv.split("\\|")
      SimpleRecord(parts(0), parts(1).toLong, parts(2), new Date(parts(3).toLong), Some(BigDecimal(parts(4))), BigDecimal(parts(5)), parts(6))
    }
  }

  def simpleRecordSource(data: List[SimpleRecord]): FlinkSourceFactory[SimpleRecord] = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleRecord](new ExecutionConfig, data, Some(ascendingTimestampExtractor), Typed[SimpleRecord]) with TestDataParserProvider[SimpleRecord] {
      override def testDataParser: TestDataParser[SimpleRecord] = newLineSplittedTestDataParser
    })


  val jsonSource: FlinkSourceFactory[SimpleJsonRecord] = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleJsonRecord](new ExecutionConfig, List(), None, Typed[SimpleJsonRecord]) with TestDataParserProvider[SimpleJsonRecord] {
      override def testDataParser: TestDataParser[SimpleJsonRecord] = new EmptyLineSplittedTestDataParser[SimpleJsonRecord] {

        override def parseElement(json: String): SimpleJsonRecord = {
          CirceUtil.decodeJsonUnsafe[SimpleJsonRecord](json, "invalid request")
        }

      }
    }
  )

  object TypedJsonSource extends FlinkSourceFactory[TypedMap] with ReturningType {

    @MethodToInvoke
    def create(processMetaData: MetaData,  @ParamName("type") definition: java.util.Map[String, _]): Source[_] = {
      new CollectionSource[TypedMap](new ExecutionConfig, List(), None, Typed[TypedMap]) with TestDataParserProvider[TypedMap] with ReturningType {

        override def testDataParser: TestDataParser[TypedMap] = new EmptyLineSplittedTestDataParser[TypedMap] {
          override def parseElement(json: String): TypedMap = {
            TypedMap(CirceUtil.decodeJsonUnsafe[Map[String, String]](json, "invalid request"))
          }
        }

        override val returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)

      }
    }

    override def returnType: typing.TypingResult = Typed[TypedMap]
  }

  @JsonCodec case class KeyValue(key: String, value: Int, date: Long)

  class KeyValueKafkaSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[KeyValue](
              new EspDeserializationSchema[KeyValue](e => CirceUtil.decodeJsonUnsafe[KeyValue](e)),
              Some(outOfOrdernessTimestampExtractor[KeyValue](_.date)),
              TestParsingUtils.newLineSplit,
              processObjectDependencies)

}
