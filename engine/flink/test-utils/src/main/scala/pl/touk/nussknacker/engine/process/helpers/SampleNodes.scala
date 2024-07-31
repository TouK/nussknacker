package pl.touk.nussknacker.engine.process.helpers

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import io.circe.Json
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.{FilterFunction, FlatMapFunction}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.streaming.api.functions.co.{CoMapFunction, RichCoFlatMapFunction}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, OneInputStreamOperator}
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits._
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.util.service.{EnricherContextTransformation, TimeMeasuringService}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import java.util.concurrent.atomic.AtomicInteger
import java.util.{Date, Optional, UUID}
import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

//TODO: clean up sample objects...
object SampleNodes {

  case class SimpleRecord(
      id: String,
      value1: Long,
      value2: String,
      date: Date,
      value3Opt: Option[BigDecimal] = None,
      value3: BigDecimal = 1,
      intAsAny: Any = 1,
      enumValue: SimpleJavaEnum = SimpleJavaEnum.ONE
  )

  case class SimpleRecordWithPreviousValue(record: SimpleRecord, previous: Long, added: String)

  case class SimpleRecordAcc(id: String, value1: Long, value2: Set[String], date: Date)

  @JsonCodec case class SimpleJsonRecord(id: String, field: String)

  class IntParamSourceFactory extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke
    def create(@ParamName("param") param: Integer) =
      new CollectionSource[Integer](list = List(param), timestampAssigner = None, returnType = Typed[Integer])

  }

  class JoinExprBranchFunction(
      valueByBranchId: Map[String, LazyParameter[AnyRef]],
      val lazyParameterHelper: FlinkLazyParameterFunctionHelper
  ) extends RichCoFlatMapFunction[Context, Context, ValueWithContext[AnyRef]]
      with LazyParameterInterpreterFunction {

    @transient lazy val end1Interpreter: Context => AnyRef =
      toEvaluateFunctionConverter.toEvaluateFunction(valueByBranchId("end1"))

    @transient lazy val end2Interpreter: Context => AnyRef =
      toEvaluateFunctionConverter.toEvaluateFunction(valueByBranchId("end2"))

    override def flatMap1(ctx: Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
      val joinContext = ctx.appendIdSuffix("end1")
      collectHandlingErrors(joinContext, out) {
        ValueWithContext(end1Interpreter(joinContext), joinContext)
      }
    }

    override def flatMap2(ctx: Context, out: Collector[ValueWithContext[AnyRef]]): Unit = {
      val joinContext = ctx.appendIdSuffix("end2")
      collectHandlingErrors(joinContext, out) {
        ValueWithContext(end2Interpreter(joinContext), joinContext)
      }
    }

  }

  class MockService(resultsHolder: => TestResultsHolder[Any])
      extends Service
      with TimeMeasuringService
      with Serializable {

    val serviceName = "mockService"

    @MethodToInvoke
    def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext): Future[Unit] = {
      measuring(Future.successful {
        resultsHolder.add(all)
      })
    }

  }

  class EnricherWithOpenService extends Service with TimeMeasuringService with Serializable {

    val serviceName = "enricherWithOpenService"

    var internalVar: String = _

    override def open(runtimeContext: EngineRuntimeContext): Unit = {
      super.open(runtimeContext)
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

    override def open(engineRuntimeContext: EngineRuntimeContext): Unit = {
      super.open(engineRuntimeContext)
      opened = true
    }

    override def close(): Unit = {
      super.close()
      closed = true
    }

  }

  object LifecycleService extends Service with WithLifecycle with Serializable {

    @MethodToInvoke
    def invoke(): Future[Unit] = {
      Future.successful(())
    }

  }

  object EagerLifecycleService extends EagerService with WithLifecycle with Serializable {

    @transient
    var list: List[(String, WithLifecycle)] = Nil

    override def open(engineRuntimeContext: EngineRuntimeContext): Unit = {
      super.open(engineRuntimeContext)
      list.foreach(_._2.open(engineRuntimeContext))
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
    def prepare(@ParamName("name") name: String): ServiceInvoker = synchronized {
      val newI = new ServiceInvoker with WithLifecycle {
        override def invoke(context: Context)(
            implicit ec: ExecutionContext,
            collector: ServiceInvocationCollector,
            componentUseCase: ComponentUseCase
        ): Future[Any] = {
          if (!opened) {
            throw new IllegalArgumentException
          }
          Future.successful(())
        }

      }
      list = (name -> newI) :: list
      newI
    }

  }

  object CollectingEagerService extends EagerService with Serializable {

    @MethodToInvoke
    def prepare(
        @ParamName("static") static: String,
        @ParamName("dynamic") dynamic: LazyParameter[String]
    ): ServiceInvoker = new ServiceInvoker {

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: ServiceInvocationCollector,
          componentUseCase: ComponentUseCase
      ): Future[Any] = {
        collector.collect(s"static-$static-dynamic-${dynamic.evaluate(context)}", Option(())) {
          Future.successful(())
        }
      }

    }

  }

  object ServiceAcceptingScalaOption extends Service with Serializable {

    @MethodToInvoke
    def invoke(@ParamName("scalaOptionParam") scalaOptionParam: Option[String]): Future[Option[String]] =
      Future.successful(scalaOptionParam)

  }

  object StateCustomNode extends CustomStreamTransformer with ExplicitUidInOperatorsSupport with Serializable {

    @MethodToInvoke(returnType = classOf[SimpleRecordWithPreviousValue])
    def execute(
        @ParamName("stringVal") stringVal: String,
        @ParamName("groupBy") groupBy: LazyParameter[String]
    )(implicit nodeId: NodeId, metaData: MetaData, componentUseCase: ComponentUseCase) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        setUidToNodeIdIfNeed(
          context,
          start
            .flatMap(context.lazyParameterHelper.lazyMapFunction(groupBy))
            .keyBy((v: ValueWithContext[String]) => v.value)
            .mapWithState[ValueWithContext[AnyRef], Long] {
              case (SimpleFromValueWithContext(ctx, sr), Some(oldState)) =>
                (ValueWithContext(SimpleRecordWithPreviousValue(sr, oldState, stringVal), ctx), Some(sr.value1))
              case (SimpleFromValueWithContext(ctx, sr), None) =>
                (ValueWithContext(SimpleRecordWithPreviousValue(sr, 0, stringVal), ctx), Some(sr.value1))
            }(context.valueWithContextInfo.forUnknown, TypeInformation.of(classOf[Long]))
        )
      })

    object SimpleFromValueWithContext {
      def unapply(vwc: ValueWithContext[_]) = Some((vwc.context, vwc.context.apply[SimpleRecord]("input")))
    }

  }

  object CustomFilter extends CustomStreamTransformer with Serializable {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("input") input: LazyParameter[String], @ParamName("stringVal") stringVal: String) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {

        start
          .filter(
            new AbstractOneParamLazyParameterFunction(input, context.lazyParameterHelper) with FilterFunction[Context] {
              override def filter(value: Context): Boolean = evaluateParameter(value) == stringVal
            }
          )
          .map(ValueWithContext[AnyRef](null, _), context.valueWithContextInfo.forUnknown)
      })

  }

  object CustomFilterContextTransformation extends CustomStreamTransformer with Serializable {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(
        @ParamName("input") input: LazyParameter[String],
        @ParamName("stringVal") stringVal: String
    ): ContextTransformation = {
      ContextTransformation
        .definedBy(Valid(_))
        .implementedBy(FlinkCustomStreamTransformation {
          (start: DataStream[Context], context: FlinkCustomNodeContext) =>
            start
              .filter(
                new AbstractOneParamLazyParameterFunction(input, context.lazyParameterHelper)
                  with FilterFunction[Context] {
                  override def filter(value: Context): Boolean = evaluateParameter(value) == stringVal
                }
              )
              .map(ValueWithContext[AnyRef](null, _), context.valueWithContextInfo.forUnknown)
        })
    }

  }

  object CustomContextClear extends CustomStreamTransformer with Serializable {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(@ParamName("value") value: LazyParameter[String]) = {
      ContextTransformation
        .definedBy((in: context.ValidationContext) => Valid(in.clearVariables))
        .implementedBy(
          FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
            start
              .flatMap(context.lazyParameterHelper.lazyMapFunction(value))
              .keyBy((value: ValueWithContext[String]) => value.value)
              .map(
                (_: ValueWithContext[String]) => ValueWithContext[AnyRef](null, Context("new")),
                context.valueWithContextInfo.forUnknown
              )
          })
        )
    }

  }

  object CustomJoin extends CustomStreamTransformer with Serializable {

    @MethodToInvoke
    def execute(@OutputVariableName outputVarName: String)(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation.join
        .definedBy((in: Map[String, context.ValidationContext]) =>
          in.head._2.clearVariables.withVariable(outputVarName, Unknown, None)
        )
        .implementedBy(new FlinkCustomJoinTransformation {
          override def transform(
              inputs: Map[String, DataStream[Context]],
              context: FlinkCustomNodeContext
          ): DataStream[ValueWithContext[AnyRef]] = {
            val inputFromIr =
              (ir: Context) => ValueWithContext(ir.variables("input").asInstanceOf[AnyRef], ir)
            inputs("end1")
              .connect(inputs("end2"))
              .map(new CoMapFunction[Context, Context, ValueWithContext[AnyRef]] {
                override def map1(value: Context): ValueWithContext[AnyRef] = inputFromIr(value)
                override def map2(value: Context): ValueWithContext[AnyRef] = inputFromIr(value)
              })
          }
        })
    }

  }

  object CustomJoinUsingBranchExpressions extends CustomStreamTransformer with Serializable {

    @MethodToInvoke
    def execute(
        @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[AnyRef]],
        @OutputVariableName variableName: String
    ): JoinContextTransformation =
      ContextTransformation.join
        .definedBy { contexts =>
          val newType = Typed.fromIterableOrUnknownIfEmpty(
            contexts.keys.toList.map(branchId => valueByBranchId(branchId).returnType)
          )
          val parent = contexts.values.flatMap(_.parent).headOption
          Valid(ValidationContext(Map(variableName -> newType), Map.empty, parent))
        }
        .implementedBy(new FlinkCustomJoinTransformation {

          override def transform(
              inputs: Map[String, DataStream[Context]],
              flinkContext: FlinkCustomNodeContext
          ): DataStream[ValueWithContext[AnyRef]] = {
            inputs("end1")
              .connect(inputs("end2"))
              .flatMap(new JoinExprBranchFunction(valueByBranchId, flinkContext.lazyParameterHelper))
          }

        })

  }

  object ExtractAndTransformTimestamp extends CustomStreamTransformer with Serializable {

    @MethodToInvoke(returnType = classOf[Long])
    def methodToInvoke(@ParamName("timestampToSet") timestampToSet: Long): FlinkCustomStreamTransformation = {
      def trans(
          str: DataStream[Context],
          ctx: FlinkCustomNodeContext
      ): DataStream[ValueWithContext[AnyRef]] = {
        val streamOperator = new AbstractStreamOperator[ValueWithContext[AnyRef]]
          with OneInputStreamOperator[Context, ValueWithContext[AnyRef]] {
          override def processElement(element: StreamRecord[Context]): Unit = {
            val valueWithContext: ValueWithContext[AnyRef] =
              ValueWithContext(element.getTimestamp.asInstanceOf[AnyRef], element.getValue)
            val outputResult = new StreamRecord[ValueWithContext[AnyRef]](valueWithContext, timestampToSet)
            output.collect(outputResult)
          }
        }
        str.transform("collectTimestammp", ctx.valueWithContextInfo.forUnknown, streamOperator)
      }

      FlinkCustomStreamTransformation(trans(_, _))
    }

  }

  object ReturningDependentTypeService extends EagerService with Serializable {

    @MethodToInvoke
    def invoke(
        @ParamName("definition") definition: java.util.List[String],
        @ParamName("toFill") toFill: LazyParameter[String],
        @ParamName("count") count: Int,
        @OutputVariableName outputVar: String
    )(implicit nodeId: NodeId): ContextTransformation = {
      val listType                        = Typed.record(definition.asScala.map(_ -> Typed[String]).toMap)
      val returnType: typing.TypingResult = Typed.genericTypeClass[java.util.List[_]](List(listType))

      EnricherContextTransformation(
        outputVar,
        returnType,
        new ServiceInvoker {
          override def invoke(context: Context)(
              implicit ec: ExecutionContext,
              collector: ServiceInvocationCollector,
              componentUseCase: ComponentUseCase
          ): Future[Any] = {
            val result = (1 to count)
              .map(_ => definition.asScala.map(_ -> toFill.evaluate(context)).toMap)
              .map(TypedMap(_))
              .toList
              .asJava
            Future.successful(result)
          }
        }
      )
    }

  }

  object LogService extends Service {

    val invocationsCount = new AtomicInteger(0)

    def clear(): Unit = {
      invocationsCount.set(0)
    }

    @MethodToInvoke
    def invoke(
        @ParamName("all") all: Any
    )(implicit ec: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
      collector.collect(s"$all-collectedDuringServiceInvocation", Option(())) {
        invocationsCount.incrementAndGet()
        Future.successful(())
      }
    }

  }

  class ThrowingService(exception: Exception) extends Service with Serializable {

    @MethodToInvoke
    def invoke(@ParamName("throw") throwing: Boolean): Future[String] = {
      if (throwing) {
        Future.failed(exception)
      } else Future.successful("")
    }

  }

  object TransformerWithTime extends CustomStreamTransformer with Serializable {

    @MethodToInvoke
    def execute(@OutputVariableName outputVarName: String, @ParamName("seconds") seconds: Int)(
        implicit nodeId: NodeId
    ) = {
      ContextTransformation
        .definedBy((in: context.ValidationContext) => in.clearVariables.withVariable(outputVarName, Typed[Int], None))
        .implementedBy(
          FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
            start
              .map(_ => 1: java.lang.Integer)
              .keyBy((_: java.lang.Integer) => "")
              .window(TumblingEventTimeWindows.of(Time.seconds(seconds)))
              .reduce((k, v) => k + v: java.lang.Integer)
              .map(
                (i: java.lang.Integer) => ValueWithContext[AnyRef](i, Context(UUID.randomUUID().toString)),
                context.valueWithContextInfo.forUnknown
              )
          })
        )
    }

  }

  object TransformerWithNullableParam extends CustomStreamTransformer with Serializable {

    @MethodToInvoke(returnType = classOf[String])
    def execute(@ParamName("param") @Nullable param: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        start
          .flatMap(context.lazyParameterHelper.lazyMapFunction[AnyRef](param))
      })

  }

  object TransformerAddingComponentUseCase extends CustomStreamTransformer with Serializable {

    @MethodToInvoke
    def execute = {
      FlinkCustomStreamTransformation((start: DataStream[Context], flinkCustomNodeContext: FlinkCustomNodeContext) => {
        val componentUseCase = flinkCustomNodeContext.componentUseCase
        start
          .map(
            (ctx: Context) => ValueWithContext[AnyRef](componentUseCase, ctx),
            flinkCustomNodeContext.valueWithContextInfo.forUnknown
          )
      })
    }

  }

  class OptionalEndingCustom(resultsHolder: => TestResultsHolder[AnyRef])
      extends CustomStreamTransformer
      with Serializable {

    override def canBeEnding: Boolean = true

    @MethodToInvoke(returnType = classOf[String])
    def execute(@ParamName("param") @Nullable param: LazyParameter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
        val afterMap = start
          .flatMap(context.lazyParameterHelper.lazyMapFunction[AnyRef](param))
        afterMap.addSink(new SinkFunction[ValueWithContext[AnyRef]] {
          override def invoke(value: ValueWithContext[AnyRef], context: SinkFunction.Context): Unit = {
            resultsHolder.add(value.value)
          }
        })
        afterMap
      })

  }

  class EagerOptionalParameterSinkFactory(resultsHolder: => TestResultsHolder[String]) extends SinkFactory {

    @MethodToInvoke
    def createSink(@ParamName("optionalStringParam") value: Optional[String]): Sink = new BasicFlinkSink {

      // Optional is not serializable...
      private val serializableValue = value.orElse(null)

      override def valueFunction(
          helper: FlinkLazyParameterFunctionHelper
      ): FlatMapFunction[Context, ValueWithContext[String]] =
        (ctx, collector) => collector.collect(ValueWithContext(serializableValue, ctx))

      override def toFlinkFunction: SinkFunction[String] = new SinkFunction[String] {
        override def invoke(value: String, context: SinkFunction.Context): Unit = resultsHolder.add(value)
      }

      override type Value = String
    }

  }

  case object MonitorEmptySink extends EmptySink with Serializable {

    val invocationsCount = new AtomicInteger(0)

    def clear(): Unit = {
      invocationsCount.set(0)
    }

    override def valueFunction(
        helper: FlinkLazyParameterFunctionHelper
    ): FlatMapFunction[Context, ValueWithContext[AnyRef]] = (_, _) => {
      invocationsCount.getAndIncrement()
    }

  }

  object SinkForInts {
    def apply(resultsHolder: => TestResultsHolder[java.lang.Integer]): SinkFactory =
      SinkForType[java.lang.Integer](resultsHolder)
  }

  object SinkForStrings {
    def apply(resultsHolder: => TestResultsHolder[String]): SinkFactory = SinkForType[String](resultsHolder)
  }

  object SinkForLongs {
    def apply(resultsHolder: => TestResultsHolder[java.lang.Long]): SinkFactory =
      SinkForType[java.lang.Long](resultsHolder)
  }

  object SinkForAny {
    def apply(resultsHolder: => TestResultsHolder[AnyRef]): SinkFactory = SinkForType[AnyRef](resultsHolder)
  }

  object EmptyService extends Service with Serializable {
    def invoke(): Future[Unit] = Future.successful(())
  }

  object GenericParametersNode
      extends CustomStreamTransformer
      with SingleInputDynamicComponent[AnyRef]
      with Serializable {

    override type State = List[String]

    private val par1ParamName: ParameterName = ParameterName("par1")
    private val mandatoryPar1Declaration     = ParameterDeclaration.mandatory[String](par1ParamName).withCreator()

    private val lazyPar1ParamName: ParameterName = ParameterName("lazyPar1")
    private val lazyPar1Declaration =
      ParameterDeclaration.lazyMandatory[java.lang.Boolean](lazyPar1ParamName).withCreator()

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(List(mandatoryPar1Declaration.createParameter(), lazyPar1Declaration.createParameter()))
      case TransformationStep(
            (`par1ParamName`, DefinedEagerParameter(value: String, _)) :: (`lazyPar1ParamName`, _) :: Nil,
            None
          ) =>
        val split = value.split(",").toList
        NextParameters(split.map(name => Parameter(ParameterName(name), Unknown)), state = Some(split))
      case TransformationStep((`par1ParamName`, FailedToDefineParameter(_)) :: (`lazyPar1ParamName`, _) :: Nil, None) =>
        outputParameters(context, dependencies, Nil)
      case TransformationStep((`par1ParamName`, _) :: (`lazyPar1ParamName`, _) :: rest, Some(names))
          if rest.map(_._1.value) == names =>
        outputParameters(context, dependencies, rest)
    }

    private def outputParameters(
        context: ValidationContext,
        dependencies: List[NodeDependencyValue],
        rest: List[(ParameterName, BaseDefinedParameter)]
    )(implicit nodeId: NodeId): this.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          val result = Typed.record(rest.map { case (k, v) => k.value -> v.returnType }.toMap)
          FinalResults.forValidation(context)(_.withVariable(OutputVar.customNode(name), result))
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): AnyRef = {
      val map  = params.nameToValueMap.filterNot(k => List(par1ParamName, lazyPar1ParamName).contains(k._1))
      val bool = lazyPar1Declaration.extractValueUnsafe(params)
      FlinkCustomStreamTransformation((stream, fctx) => {
        stream
          .filter(new LazyParameterFilterFunction(bool, fctx.lazyParameterHelper))
          .map(
            (ctx: Context) => ValueWithContext[AnyRef](TypedMap(map.map { case (k, v) => (k.value, v) }), ctx),
            fctx.valueWithContextInfo.forUnknown
          )
      })
    }

    override def nodeDependencies: List[NodeDependency] =
      List(OutputVariableNameDependency, TypedNodeDependency[MetaData])

  }

  object NodePassingStateToImplementation
      extends CustomStreamTransformer
      with SingleInputDynamicComponent[AnyRef]
      with Serializable {

    val VariableThatshouldBeDefinedBeforeNodeName = "foo"

    override type State = Boolean

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
      context
        .withVariable(OutputVar.customNode(OutputVariableNameDependency.extract(dependencies)), Typed[Boolean])
        .map(FinalResults(_, state = Some(context.contains(VariableThatshouldBeDefinedBeforeNodeName))))
        .valueOr(errors => FinalResults(context, errors.toList))
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): AnyRef = {
      FlinkCustomStreamTransformation((stream, fctx) => {
        stream
          .map(
            (ctx: Context) => ValueWithContext[AnyRef](finalState.get: java.lang.Boolean, ctx),
            fctx.valueWithContextInfo.forUnknown
          )
      })
    }

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)

  }

  object GenericParametersSource
      extends SourceFactory
      with UnboundedStreamComponent
      with SingleInputDynamicComponent[Source]
      with Serializable {

    override type State = Nothing

    private val aTypeParamName: ParameterName = ParameterName("type")

    private val aTypeDeclaration = ParameterDeclaration
      .mandatory[String](aTypeParamName)
      .withCreator(modify =
        _.copy(editor =
          Some(
            FixedValuesParameterEditor(
              List(FixedExpressionValue("'type1'", "type1"), FixedExpressionValue("'type2'", "type2"))
            )
          )
        )
      )

    private val versionParamName: ParameterName = ParameterName("version")

    private val versionDeclaration = ParameterDeclaration
      .mandatory[Int](versionParamName)
      .withAdvancedCreator[List[Int]](
        create = versions =>
          _.copy(editor =
            Some(FixedValuesParameterEditor(versions.map(v => FixedExpressionValue(v.toString, v.toString))))
          )
      )

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(aTypeDeclaration.createParameter() :: Nil)
      case TransformationStep((`aTypeParamName`, DefinedEagerParameter(value: String, _)) :: Nil, None) =>
        // This is just sample, so we don't care about all cases, in *real* transformer we would e.g. take lists from config file, external service etc.
        val intVersions = value match {
          case "type1" => List(1, 2)
          case "type2" => List(3, 4)
          case _       => ???
        }
        NextParameters(versionDeclaration.createParameter(intVersions) :: Nil)
      case TransformationStep((`aTypeParamName`, FailedToDefineParameter(_)) :: Nil, None) =>
        output(context, dependencies)
      case TransformationStep((`aTypeParamName`, _) :: (`versionParamName`, _) :: Nil, None) =>
        output(context, dependencies)
    }

    private def output(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId) = {
      val name = dependencies.collectFirst { case OutputVariableNameValue(name) =>
        name
      }.get

      FinalResults.forValidation(context)(_.withVariable(OutputVar.customNode(name), Typed[String]))
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): Source = {
      val aTypeValue   = aTypeDeclaration.extractValueUnsafe(params)
      val versionValue = versionDeclaration.extractValueUnsafe(params)
      val out          = s"$aTypeValue-$versionValue"
      CollectionSource(out :: Nil, None, Typed[String])
    }

    override def nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: Nil
  }

  object GenericSourceWithCustomVariables
      extends SourceFactory
      with UnboundedStreamComponent
      with SingleInputDynamicComponent[Source]
      with Serializable {

    private class CustomFlinkContextInitializer extends BasicContextInitializer[String](Typed[String]) {

      override def validationContext(
          context: ValidationContext
      )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
        // Append variable "input"
        val contextWithInput = super.validationContext(context)

        // Specify additional variables
        val additionalVariables = Map(
          "additionalOne" -> Typed[String],
          "additionalTwo" -> Typed[Int]
        )

        // Append additional variables to ValidationContext
        additionalVariables.foldLeft(contextWithInput) { case (acc, (name, typingResult)) =>
          acc.andThen(_.withVariable(name, typingResult, None))
        }
      }

      override def initContext(contextIdGenerator: ContextIdGenerator): ContextInitializingFunction[String] =
        new BasicContextInitializingFunction[String](contextIdGenerator, outputVariableName) {

          override def apply(input: String): Context = {
            // perform some transformations and/or computations
            val additionalVariables = Map[String, Any](
              "additionalOne" -> s"transformed:${input}",
              "additionalTwo" -> input.length()
            )
            // initialize context with input variable and append computed values
            super.apply(input).withVariables(additionalVariables)
          }

        }

    }

    override type State = Nothing

    // There is only one parameter in this source
    private val elementsParamName = ParameterName("elements")
    private val elementsDeclaration =
      ParameterDeclaration.mandatory[java.util.List[String]](elementsParamName).withCreator()

    private val customContextInitializer: ContextInitializer[String] = new CustomFlinkContextInitializer

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): GenericSourceWithCustomVariables.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(elementsDeclaration.createParameter() :: Nil)
      case TransformationStep((`elementsParamName`, _) :: Nil, None) =>
        FinalResults.forValidation(context)(customContextInitializer.validationContext)
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): Source = {
      import scala.jdk.CollectionConverters._
      val elementsValue = elementsDeclaration.extractValueUnsafe(params).asScala.toList

      new CollectionSource(
        list = elementsValue,
        timestampAssigner = None,
        returnType = Typed[String],
      ) with TestDataGenerator with FlinkSourceTestSupport[String] {
        override val contextInitializer: ContextInitializer[ProcessingType] = customContextInitializer

        override def generateTestData(size: Int): TestData = TestData(
          elementsValue.map(e => TestRecord(Json.fromString(e)))
        )

        override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord =>
            CirceUtil.decodeJsonUnsafe[String](testRecord.json)
          }

        override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner
      }
    }

    override def nodeDependencies: List[NodeDependency] = Nil

  }

  class GenericParametersSink(resultsHolder: => TestResultsHolder[String])
      extends SinkFactory
      with SingleInputDynamicComponent[Sink]
      with Serializable {

    private val componentUseCaseDependency = TypedNodeDependency[ComponentUseCase]

    override type State = Nothing

    private val valueParamName: ParameterName = ParameterName("value")
    private val valueParamDeclaration         = ParameterDeclaration.lazyMandatory[String](valueParamName).withCreator()

    private val aTypeParamName = ParameterName("type")

    private val aTypeParamDeclaration = ParameterDeclaration
      .mandatory[String](aTypeParamName)
      .withCreator(
        modify = _.copy(editor =
          Some(
            FixedValuesParameterEditor(
              List(FixedExpressionValue("'type1'", "type1"), FixedExpressionValue("'type2'", "type2"))
            )
          )
        )
      )

    private val versionParamName = ParameterName("version")

    private val versionParamDeclaration = ParameterDeclaration
      .mandatory[Int](versionParamName)
      .withAdvancedCreator[List[Int]](
        create = versions =>
          _.copy(editor =
            Some(FixedValuesParameterEditor(versions.map(v => FixedExpressionValue(v.toString, v.toString))))
          )
      )

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): this.ContextTransformationDefinition = {
      case TransformationStep(Nil, _) =>
        NextParameters(valueParamDeclaration.createParameter() :: aTypeParamDeclaration.createParameter() :: Nil)
      case TransformationStep(
            (`valueParamName`, _) :: (`aTypeParamName`, DefinedEagerParameter(value: String, _)) :: Nil,
            None
          ) =>
        val intVersions = value match {
          case "type1" => List(1, 2)
          case "type2" => List(3, 4)
          case _       => ???
        }
        NextParameters(versionParamDeclaration.createParameter(intVersions) :: Nil)
      case TransformationStep((`valueParamName`, _) :: (`aTypeParamName`, FailedToDefineParameter(_)) :: Nil, None) =>
        FinalResults(context)
      case TransformationStep((`valueParamName`, _) :: (`aTypeParamName`, _) :: (`versionParamName`, _) :: Nil, None) =>
        FinalResults(context)
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[State]
    ): FlinkSink = new FlinkSink {

      type Value = String

      private val typeValue    = aTypeParamDeclaration.extractValueUnsafe(params)
      private val versionValue = versionParamDeclaration.extractValueUnsafe(params)

      override def prepareValue(
          dataStream: DataStream[Context],
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStream[ValueWithContext[Value]] = {
        dataStream
          .flatMap(
            flinkNodeContext.lazyParameterHelper.lazyMapFunction(valueParamDeclaration.extractValueUnsafe(params))
          )
          .map(
            (v: ValueWithContext[String]) =>
              v.copy(value =
                s"${v.value}+$typeValue-$versionValue+componentUseCase:${componentUseCaseDependency.extract(dependencies)}"
              ),
            flinkNodeContext.valueWithContextInfo.forType(TypeInformation.of(classOf[String]))
          )
      }

      override def registerSink(
          dataStream: DataStream[ValueWithContext[String]],
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSink[_] =
        dataStream.map(_.value).addSink(new SinkForTypeFunction[String](resultsHolder))

    }

    override def nodeDependencies: List[NodeDependency] = List(componentUseCaseDependency)
  }

  object ProcessHelper {

    val constant = 4

    def add(a: Int, b: Int): Int = a + b

    def scalaOptionValue: Option[String] = Some("" + constant)

    def javaOptionalValue: Optional[String] = Optional.of("" + constant)

    def extractProperty(map: java.util.Map[String, _], property: String): Any = map.get(property)

  }

  private val ascendingTimestampExtractor = new StandardTimestampWatermarkHandler[SimpleRecord](
    WatermarkStrategy
      .forMonotonousTimestamps[SimpleRecord]()
      .withTimestampAssigner(StandardTimestampWatermarkHandler.toAssigner[SimpleRecord](_.date.getTime))
  )

  private val simpleRecordParser = new TestRecordParser[SimpleRecord] {

    override def parse(testRecords: List[TestRecord]) = testRecords.map { testRecord =>
      val parts = CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|")
      SimpleRecord(
        parts(0),
        parts(1).toLong,
        parts(2),
        new Date(parts(3).toLong),
        Some(BigDecimal(parts(4))),
        BigDecimal(parts(5)),
        parts(6)
      )
    }

  }

  def simpleRecordSource(data: List[SimpleRecord]): SourceFactory =
    SourceFactory.noParamUnboundedStreamFactory[SimpleRecord](
      new CollectionSource[SimpleRecord](data, Some(ascendingTimestampExtractor), Typed[SimpleRecord])
        with FlinkSourceTestSupport[SimpleRecord] {
        override def testRecordParser: TestRecordParser[SimpleRecord]                          = simpleRecordParser
        override def timestampAssignerForTest: Option[TimestampWatermarkHandler[SimpleRecord]] = timestampAssigner
      }
    )

  val jsonSource: SourceFactory = SourceFactory.noParamUnboundedStreamFactory[SimpleJsonRecord](
    new CollectionSource[SimpleJsonRecord](List(), None, Typed[SimpleJsonRecord])
      with FlinkSourceTestSupport[SimpleJsonRecord] {

      override def testRecordParser: TestRecordParser[SimpleJsonRecord] = (testRecords: List[TestRecord]) =>
        testRecords.map { testRecord =>
          CirceUtil.decodeJsonUnsafe[SimpleJsonRecord](testRecord.json, "invalid request")
        }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[SimpleJsonRecord]] = timestampAssigner
    }
  )

  object TypedJsonSource extends SourceFactory with UnboundedStreamComponent with ReturningType with Serializable {

    @MethodToInvoke
    def create(
        processMetaData: MetaData,
        componentUseCase: ComponentUseCase,
        @ParamName("type") definition: java.util.Map[String, _]
    ): Source = {
      new CollectionSource[TypedMap](List(), None, Typed[TypedMap])
        with FlinkSourceTestSupport[TypedMap]
        with ReturningType {

        override def testRecordParser: TestRecordParser[TypedMap] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord =>
            TypedMap(CirceUtil.decodeJsonUnsafe[Map[String, String]](testRecord.json, "invalid request"))
          }

        override val returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)

        override def timestampAssignerForTest: Option[TimestampWatermarkHandler[TypedMap]] = timestampAssigner
      }
    }

    override def returnType: typing.TypingResult = Typed[TypedMap]
  }

  @JsonCodec case class KeyValue(key: String, value: Int, date: Long)

  object ReturningComponentUseCaseService extends Service with Serializable {

    @MethodToInvoke
    def invoke(implicit componentUseCase: ComponentUseCase): Future[ComponentUseCase] = {
      Future.successful(componentUseCase)
    }

  }

  object CountingNodesListener extends EmptyProcessListener {
    @volatile private var nodesEntered: List[String] = Nil
    @volatile private var listening                  = false

    def listen(body: => Unit): List[String] = {
      nodesEntered = Nil
      listening = true
      body
      listening = false
      nodesEntered
    }

    override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
      if (listening) nodesEntered = nodesEntered ::: nodeId :: Nil
    }

  }

  class LifecycleCheckingListener extends ProcessListener with Lifecycle {

    @transient @volatile private var opened = false
    @transient @volatile private var closed = false

    override def open(context: EngineRuntimeContext): Unit = {
      opened = true
    }

    override def close(): Unit = {
      closed = true
    }

    override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit =
      checkValidState("nodeEntered")

    override def endEncountered(
        nodeId: String,
        ref: String,
        context: Context,
        processMetaData: MetaData
    ): Unit =
      checkValidState("endEncountered")

    override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit =
      checkValidState("deadEndEncountered")

    override def expressionEvaluated(
        nodeId: String,
        expressionId: String,
        expression: String,
        context: Context,
        processMetaData: MetaData,
        result: Any
    ): Unit =
      checkValidState("expressionEvaluated")

    override def serviceInvoked(
        nodeId: String,
        id: String,
        context: Context,
        processMetaData: MetaData,
        result: Try[Any]
    ): Unit =
      checkValidState("serviceInvoked")

    override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit =
      checkValidState("exceptionThrown")

    private def checkValidState(operation: String): Unit = {
      if (!opened)
        throw new IllegalStateException(s"For operation: $operation, ${getClass.getSimpleName} wasn't opened")
      if (closed)
        throw new IllegalStateException(s"For operation: $operation, ${getClass.getSimpleName} was already closed")
    }

  }

}
