package pl.touk.nussknacker.engine.process.helpers

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Date, Optional, UUID}

import cats.data.Validated.Valid
import com.github.ghik.silencer.silent
import io.circe.generic.JsonCodec
import javax.annotation.Nullable
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.{FilterFunction, MapFunction}
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.co.RichCoMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{EmptyLineSplittedTestDataParser, NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, ServiceReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{BasicContextInitializingFunction, _}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.flink.util.signal.KafkaSignalStreamConnector
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EspDeserializationSchema}
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.FixedRecordFormatterFactoryWrapper
import pl.touk.nussknacker.engine.kafka.source.{InputMeta, KafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.{BasicRecordFormatter, KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.test.WithDataList

import scala.annotation.nowarn
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

//TODO: clean up sample objects...
object SampleNodes {

  val RecordingExceptionHandler = new RecordingExceptionHandler

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

  class JoinExprBranchFunction(valueByBranchId: Map[String, LazyParameter[AnyRef]],
                               val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
    extends RichCoMapFunction[Context, Context, ValueWithContext[AnyRef]] with LazyParameterInterpreterFunction {

    @transient lazy val end1Interpreter: Context => AnyRef =
      lazyParameterInterpreter.syncInterpretationFunction(valueByBranchId("end1"))

    @transient lazy val end2Interpreter: Context => AnyRef =
      lazyParameterInterpreter.syncInterpretationFunction(valueByBranchId("end2"))

    override def map1(ctx: Context): ValueWithContext[AnyRef] = {
      ValueWithContext(end1Interpreter(ctx), ctx)
    }

    override def map2(ctx: Context): ValueWithContext[AnyRef] = {
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

  trait WithLifecycle extends Lifecycle {

    var opened: Boolean = false
    var closed: Boolean = false

    def reset(): Unit = {
      opened = false
      closed = false
    }

    override def open(jobData: JobData): Unit = {
      opened = true
    }

    override def close(): Unit = {
      closed = true
    }

  }

  object LifecycleService extends Service with WithLifecycle {

    @MethodToInvoke
    def invoke(): Future[Unit] = {
      Future.successful(())
    }
  }

  object EagerLifecycleService extends EagerService with WithLifecycle {

    var list: List[(String, WithLifecycle)] = Nil

    override def open(jobData: JobData): Unit = {
      super.open(jobData)
      list.foreach(_._2.open(jobData))
    }

    override def close(): Unit = {
      super.close()
      list.foreach(_._2.close())
    }

    override def reset(): Unit = synchronized {
      super.reset()
      list = Nil
    }

    @MethodToInvoke
    def invoke(@ParamName("name") name: String): ServiceInvoker = synchronized {
      val newI = new ServiceInvoker with WithLifecycle {
        override def invokeService(params: Map[String, Any])
                                  (implicit ec: ExecutionContext,
                                   collector: ServiceInvocationCollector, contextId: ContextId): Future[Any] = {
          if (!opened) {
            throw new IllegalArgumentException
          }
          Future.successful(())
        }

        override def returnType: TypingResult = Typed[Void]
      }
      list = (name -> newI)::list
      newI
    }

  }

  object CollectingEagerService extends EagerService {

    @MethodToInvoke
    def invoke(@ParamName("static") static: String, @ParamName("dynamic") dynamic: LazyParameter[String]): ServiceInvoker = new ServiceInvoker {
      override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                           collector: ServiceInvocationCollector,
                                                           contextId: ContextId): Future[Any] = {
        collector.collect(s"static-$static-dynamic-${params("dynamic")}", Option(())) {
          Future.successful(())
        }
      }

      override def returnType: TypingResult = Typed[Void]
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
          .mapWithState[ValueWithContext[AnyRef], Long] {
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
        .map(ValueWithContext[AnyRef](null, _))
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
              .map(ValueWithContext[AnyRef](null, _))
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
          .map(_ => ValueWithContext[AnyRef](null, Context("new")))
      })

  }

  object CustomJoin extends CustomStreamTransformer {

    override val clearsContext = true

    @MethodToInvoke
    def execute(): FlinkCustomJoinTransformation =
      new FlinkCustomJoinTransformation {
        override def transform(inputs: Map[String, DataStream[Context]], context: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
          val inputFromIr = (ir: Context) => ValueWithContext(ir.variables("input").asInstanceOf[AnyRef], ir)
          inputs("end1")
            .connect(inputs("end2"))
            .map(inputFromIr, inputFromIr)
        }
      }

  }

  object CustomJoinUsingBranchExpressions extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(@BranchParamName("value") valueByBranchId: Map[String, LazyParameter[AnyRef]],
                @OutputVariableName variableName: String): JoinContextTransformation =
      ContextTransformation
        .join.definedBy { contexts =>
        val newType = Typed(contexts.keys.toList.map(branchId => valueByBranchId(branchId).returnType): _*)
        val parent = contexts.values.flatMap(_.parent).headOption
        Valid(ValidationContext(Map(variableName -> newType), Map.empty, parent))
      }.implementedBy(
        new FlinkCustomJoinTransformation {
          override def transform(inputs: Map[String, DataStream[Context]],
                                 flinkContext: FlinkCustomNodeContext): DataStream[ValueWithContext[AnyRef]] = {
            inputs("end1")
              .connect(inputs("end2"))
              .map(new JoinExprBranchFunction(valueByBranchId, flinkContext.lazyParameterHelper))
          }
        })

  }

  // Remove @silent after upgrade to silencer 1.7
  @silent("deprecated")
  @nowarn("cat=deprecation")
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
        .map(definition => TypedObjectTypingResult(definition.asInstanceOf[java.util.List[String]].asScala.map(_ -> Typed[String]).toList))
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
                (_:Array[Byte]) => ValueWithContext[AnyRef]("", Context("id")))
    })
  }

  object TransformerWithTime extends CustomStreamTransformer {

    override def clearsContext = true

    @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
    @MethodToInvoke(returnType = classOf[java.lang.Integer])
    def execute(@ParamName("seconds") seconds: Int) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        start
          .map(_ => 1)
          .timeWindowAll(Time.seconds(seconds)).reduce(_ + _)
          .map(i => ValueWithContext[AnyRef](i.underlying(), Context(UUID.randomUUID().toString)))
      })
  }

  object TransformerWithNullableParam extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[String])
    def execute(@ParamName("param") @Nullable param: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        start
          .map(context.lazyParameterHelper.lazyMapFunction[AnyRef](param))
      })

  }

  object OptionalEndingCustom extends CustomStreamTransformer {
    
    override def canBeEnding: Boolean = true

    @MethodToInvoke
    def execute(@ParamName("param") @Nullable param: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        val afterMap = start.map(context.lazyParameterHelper.lazyMapFunction[AnyRef](param))
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
    def createSink(@ParamName("intParam") value: LazyParameter[java.lang.Integer]): Sink = new FlinkSink {

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

  case object SinkForInputMeta extends BasicFlinkSink with WithDataList[InputMeta[Any]] {
    override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
      override def invoke(value: Any): Unit = {
        add(value.asInstanceOf[InputMeta[Any]])
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

    private def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): this.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          val result = TypedObjectTypingResult(rest.map { case (k, v) => k -> v.returnType })
          context.withVariable(OutputVar.customNode(name), result).fold(
            errors => FinalResults(context, errors.toList),
            FinalResults(_))
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def initialParameters: List[Parameter] = List(
      Parameter[String]("par1"), Parameter[java.lang.Boolean]("lazyPar1").copy(isLazyParameter = true)
    )

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = {
      val map = params.filterNot(k => List("par1", "lazyPar1").contains(k._1))
      val bool = params("lazyPar1").asInstanceOf[LazyParameter[java.lang.Boolean]]
      FlinkCustomStreamTransformation((stream, fctx) => {
        stream
          .filter(new LazyParameterFilterFunction(bool, fctx.lazyParameterHelper))
          .map(ctx => ValueWithContext[AnyRef](TypedMap(map), ctx))
      })
    }

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency(classOf[MetaData]))


  }

  object NodePassingStateToImplementation extends CustomStreamTransformer with SingleInputGenericNodeTransformation[AnyRef] {

    val VariableThatShouldBeDefinedBeforeNodeName = "foo"

    override type State = Boolean

    override def contextTransformation(context: ValidationContext,
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        context.withVariable(OutputVar.customNode(OutputVariableNameDependency.extract(dependencies)), Typed[Boolean])
          .map(FinalResults(_, state = Some(context.contains(VariableThatShouldBeDefinedBeforeNodeName))))
          .valueOr( errors => FinalResults(context, errors.toList))
    }

    override def initialParameters: List[Parameter] = List.empty

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = {
      FlinkCustomStreamTransformation((stream, fctx) => {
        stream
          .map(ctx => ValueWithContext[AnyRef](finalState.get: java.lang.Boolean, ctx))
      })
    }

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  }



  object GenericParametersSource extends FlinkSourceFactory[AnyRef] with SingleInputGenericNodeTransformation[Source[AnyRef]] {

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId)
      : this.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("type", DefinedEagerParameter(value: String, _))::Nil, None) =>
        //This is just sample, so we don't care about all cases, in *real* transformer we would e.g. take lists from config file, external service etc.
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
      val name = dependencies.collectFirst {
        case OutputVariableNameValue(name) => name
      }.get

      context.withVariable(OutputVar.customNode(name), Typed[String]).fold(
        errors => FinalResults(context, errors.toList),
        FinalResults(_)
      )
    }

    override def initialParameters: List[Parameter] = Parameter[String]("type")
      .copy(editor = Some(FixedValuesParameterEditor(List(FixedExpressionValue("'type1'", "type1"), FixedExpressionValue("'type2'", "type2"))))) :: Nil

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source[AnyRef] = {
      val out = params("type") + "-" + params("version")
      CollectionSource(StreamExecutionEnvironment.getExecutionEnvironment.getConfig, out::Nil, None, Typed[String])
    }

    override def nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: Nil
  }

  object GenericSourceWithCustomVariables extends FlinkSourceFactory[String] with SingleInputGenericNodeTransformation[Source[String]] {

    private class CustomFlinkContextInitializer extends BasicFlinkGenericContextInitializer[String, DefinedParameter, State] {

      override def validationContext(context: ValidationContext, dependencies: List[NodeDependencyValue], parameters: List[(String, DefinedParameter)], state: Option[State])(implicit nodeId: NodeId): ValidationContext = {
        //Append variable "input"
        val contextWithInput = super.validationContext(context, dependencies, parameters, state)

        //Specify additional variables
        val additionalVariables = Map(
          "additionalOne" -> Typed[String],
          "additionalTwo" -> Typed[Int]
        )

        //Append additional variables to ValidationContext
        additionalVariables.foldLeft(contextWithInput) { case (acc, (name, typingResult)) =>
          acc.withVariable(name, typingResult, None).getOrElse(acc)
        }
      }

      override protected def outputVariableType(context: ValidationContext, dependencies: List[NodeDependencyValue],
                                                parameters: List[(String, DefinedSingleParameter)], state: Option[Nothing])
                                               (implicit nodeId: NodeId): typing.TypingResult = Typed[String]

      override def initContext(processId: String, taskName: String): MapFunction[String, Context] = {
        new BasicContextInitializingFunction[String](processId, taskName) {
          override def map(input: String): Context = {
            //perform some transformations and/or computations
            val additionalVariables = Map[String, Any](
              "additionalOne" -> s"transformed:${input}",
              "additionalTwo" -> input.length()
            )
            //initialize context with input variable and append computed values
            super.map(input).withVariables(additionalVariables)
          }
        }
      }

    }

    override type State = Nothing

    //There is only one parameter in this source
    private val elementsParamName = "elements"

    private val customContextInitializer: BasicFlinkGenericContextInitializer[String, DefinedParameter, State] = new CustomFlinkContextInitializer

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId)
    : GenericSourceWithCustomVariables.NodeTransformationDefinition = {
      //Component has simple parameters based only on initialParameters.
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case step@TransformationStep((`elementsParamName`, _) :: Nil, None) =>
        FinalResults(customContextInitializer.validationContext(context, dependencies, step.parameters, step.state))
    }

    override def initialParameters: List[Parameter] = Parameter[java.util.List[String]](`elementsParamName`) :: Nil

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Source[String] = {
      import scala.collection.JavaConverters._
      val elements = params(`elementsParamName`).asInstanceOf[java.util.List[String]].asScala.toList

      new CollectionSource[String](StreamExecutionEnvironment.getExecutionEnvironment.getConfig, elements, None, Typed[String])
        with TestDataGenerator
        with FlinkSourceTestSupport[String] {

        override val contextInitializer: FlinkContextInitializer[String] = customContextInitializer

        override def generateTestData(size: Int): Array[Byte] = elements.mkString("\n").getBytes

        override def testDataParser: TestDataParser[String] = new NewLineSplittedTestDataParser[String] {
          override def parseElement(testElement: String): String = testElement
        }

        override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner
      }
    }

    override def nodeDependencies: List[NodeDependency] = Nil

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

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): FlinkSink = {
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

  private val ascendingTimestampExtractor = new StandardTimestampWatermarkHandler[SimpleRecord](WatermarkStrategy
    .forMonotonousTimestamps[SimpleRecord]().withTimestampAssigner(StandardTimestampWatermarkHandler.timestampAssigner[SimpleRecord](_.date.getTime)))

  private def outOfOrdernessTimestampExtractor[T](extract: T => Long) = new StandardTimestampWatermarkHandler(
    WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofMinutes(10)).withTimestampAssigner(StandardTimestampWatermarkHandler.timestampAssigner(extract))
  )

  private val newLineSplittedTestDataParser = new NewLineSplittedTestDataParser[SimpleRecord] {
    override def parseElement(csv: String): SimpleRecord = {
      val parts = csv.split("\\|")
      SimpleRecord(parts(0), parts(1).toLong, parts(2), new Date(parts(3).toLong), Some(BigDecimal(parts(4))), BigDecimal(parts(5)), parts(6))
    }
  }

  def simpleRecordSource(data: List[SimpleRecord]): FlinkSourceFactory[SimpleRecord] = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleRecord](new ExecutionConfig, data, Some(ascendingTimestampExtractor), Typed[SimpleRecord]) with FlinkSourceTestSupport[SimpleRecord] {
      override def testDataParser: TestDataParser[SimpleRecord] = newLineSplittedTestDataParser

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[SimpleRecord]] = timestampAssigner
    })


  val jsonSource: FlinkSourceFactory[SimpleJsonRecord] = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleJsonRecord](new ExecutionConfig, List(), None, Typed[SimpleJsonRecord]) with FlinkSourceTestSupport[SimpleJsonRecord] {
      override def testDataParser: TestDataParser[SimpleJsonRecord] = new EmptyLineSplittedTestDataParser[SimpleJsonRecord] {

        override def parseElement(json: String): SimpleJsonRecord = {
          CirceUtil.decodeJsonUnsafe[SimpleJsonRecord](json, "invalid request")
        }

      }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[SimpleJsonRecord]] = timestampAssigner
    }
  )

  object TypedJsonSource extends FlinkSourceFactory[TypedMap] with ReturningType {

    @MethodToInvoke
    def create(processMetaData: MetaData,  @ParamName("type") definition: java.util.Map[String, _]): Source[_] = {
      new CollectionSource[TypedMap](new ExecutionConfig, List(), None, Typed[TypedMap]) with FlinkSourceTestSupport[TypedMap] with ReturningType {

        override def testDataParser: TestDataParser[TypedMap] = new EmptyLineSplittedTestDataParser[TypedMap] {
          override def parseElement(json: String): TypedMap = {
            TypedMap(CirceUtil.decodeJsonUnsafe[Map[String, String]](json, "invalid request"))
          }
        }

        override val returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)

        override def timestampAssignerForTest: Option[TimestampWatermarkHandler[TypedMap]] = timestampAssigner
      }
    }

    override def returnType: typing.TypingResult = Typed[TypedMap]
  }

  @JsonCodec case class KeyValue(key: String, value: Int, date: Long)

  class KeyValueKafkaSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, KeyValue](
              new FixedValueDeserializationSchemaFactory(new EspDeserializationSchema[KeyValue](e => CirceUtil.decodeJsonUnsafe[KeyValue](e))),
              Some(outOfOrdernessTimestampExtractor[ConsumerRecord[String, KeyValue]](_.value().date)),
              FixedRecordFormatterFactoryWrapper(BasicRecordFormatter()),
              processObjectDependencies)

}
