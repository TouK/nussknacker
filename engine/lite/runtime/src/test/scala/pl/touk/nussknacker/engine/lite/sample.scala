package pl.touk.nussknacker.engine.lite

import cats.Monad
import cats.data.{State, StateT, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ComponentUseCase
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentType,
  NodeComponentInfo,
  NodesDeploymentData,
  UnboundedStreamComponent
}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.lite.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{
  CapabilityTransformer,
  CustomComponentContext,
  LiteSource
}
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.utils.transformers.ContextMappingComponent
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime.syncEc

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.language.higherKinds

/*
  This is sample engine, with simple state - a map of counters, and simple aggregation based on this state. Mainly for testing purposes
 */
object sample {

  case object SourceFailure extends Exception("Source failure")

  case class SampleInput(contextId: String, value: Int)

  case class SampleInputWithListAndMap(
      contextId: String,
      numbers: java.util.List[Long],
      additionalParams: java.util.Map[String, Any]
  )

  implicit val shape: InterpreterShape[StateType] = new InterpreterShape[StateType] {

    import InterpreterShape._

    override def fromFuture[T]: Future[T] => StateType[Either[T, Throwable]] = { f =>
      StateT.pure(Await.result(transform(f)(SynchronousExecutionContextAndIORuntime.syncEc), 1 second))
    }

  }

  implicit val capabilityTransformer: CapabilityTransformer[StateType] = new FixedCapabilityTransformer[StateType]

  type StateType[M] = State[Map[String, Double], M]

  private val components: List[ComponentDefinition] = List(
    ComponentDefinition("sum", SumTransformerFactory),
    ComponentDefinition("start", SimpleSourceFactory),
    ComponentDefinition("parametersSupport", SimpleSourceWithParameterTestingFactory),
    ComponentDefinition("failOnNumber1Source", FailOnNumber1SourceFactory),
    ComponentDefinition("failOnNumber1", FailOnNumber1),
    ComponentDefinition("noOpProcessor", NoOpProcessor),
    ComponentDefinition("sumNumbers", SumNumbers),
    ComponentDefinition("end", SimpleSinkFactory)
  )

  private val modelData: LocalModelData =
    LocalModelData(ConfigFactory.empty(), components, configCreator = WithUtilConfigCreator)

  def run(
      scenario: CanonicalProcess,
      data: ScenarioInputBatch[SampleInput],
      initialState: Map[String, Double],
      runtimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp
  ): ResultType[EndResult[AnyRef]] = {
    val jobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[StateType, SampleInput, AnyRef](
        scenario,
        jobData,
        modelData,
        Nil,
        ProductionServiceInvocationCollector,
        ComponentUseCase.EngineRuntime,
        nodesDeploymentData = NodesDeploymentData.empty
      )
      .fold(k => throw new IllegalArgumentException(k.toString()), identity)
    interpreter.open(runtimeContextPreparer.prepare(jobData))
    val interpreterResult      = interpreter.invoke(data)
    val resultWithInitialState = interpreterResult.runA(initialState).value
    resultWithInitialState
  }

  def test(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      scenarioTestData: ScenarioTestData
  ): TestResults[_] = {
    implicit val effectUnwrapper: EffectUnwrapper[StateType] = new EffectUnwrapper[StateType] {
      override def apply[A](fa: StateType[A]): A = fa.runA(Map.empty).value
    }
    val testRunner = new InterpreterTestRunner[StateType, SampleInput, AnyRef]
    testRunner.runTest(modelData, JobData(scenario.metaData, processVersion), scenarioTestData, scenario)
  }

  class SumTransformer(name: String, outputVar: String, value: LazyParameter[java.lang.Double])
      extends ContextMappingComponent {

    override def createStateTransformation[F[_]: Monad](
        context: CustomComponentContext[F]
    ): Context => F[Context] = {
      val convert = context.capabilityTransformer
        .transform[StateType]
        .getOrElse(throw new IllegalArgumentException("No capability!"))
      (ctx: Context) =>
        convert(State((current: Map[String, Double]) => {
          val newValue = current.getOrElse(name, 0d) + value.evaluate(ctx)
          (current + (name -> newValue), ctx.withVariable(outputVar, newValue))
        }))
    }

  }

  class UtilHelpers {
    def largestListElement(list: java.util.List[Long]): Long = list.asScala.max
  }

  object WithUtilConfigCreator extends EmptyProcessConfigCreator {

    override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig =
      ExpressionConfig(
        Map("UTIL" -> WithCategories.anyCategory(new UtilHelpers)),
        List.empty
      )

  }

  object FailOnNumber1 extends Service {

    @MethodToInvoke
    def invoke(@ParamName("value") value: Integer): Future[Integer] =
      if (value == 1) Future.failed(new IllegalArgumentException("Should not happen :)")) else Future.successful(value)

  }

  object SumNumbers extends Service {

    @MethodToInvoke
    def invoke(@ParamName("value") value: java.util.List[Long]): Future[java.lang.Long] =
      Future.successful(value.asScala.sum)

  }

  object NoOpProcessor extends Service {
    @MethodToInvoke
    def invoke(@ParamName("value") value: Integer): Future[Unit] = Future.unit
  }

  object SumTransformerFactory extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Double])
    def invoke(
        @ParamName("name") name: String,
        @ParamName("value") value: LazyParameter[java.lang.Double],
        @OutputVariableName outputVar: String
    ) = new SumTransformer(name, outputVar, value)

  }

  object SimpleSourceFactory extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke
    def create(): Source = new LiteSource[SampleInput] with SourceTestSupport[SampleInput] {

      override def createTransformation[F[_]: Monad](
          evaluateLazyParameter: CustomComponentContext[F]
      ): SampleInput => ValidatedNel[ErrorType, Context] =
        input => Valid(Context(input.contextId, Map("input" -> input.value), None))

      override def testRecordParser: TestRecordParser[SampleInput] = (testRecords: List[TestRecord]) =>
        testRecords.map { testRecord =>
          val fields = CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|")
          SampleInput(fields(0), fields(1).toInt)
        }

    }

  }

  object FailOnNumber1SourceFactory extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke
    def create()(implicit nodeId: NodeId): Source = new LiteSource[SampleInput] {

      override def createTransformation[F[_]: Monad](
          evaluateLazyParameter: CustomComponentContext[F]
      ): SampleInput => ValidatedNel[ErrorType, Context] =
        input => {
          if (input.value == 1) {
            Invalid(
              NuExceptionInfo(
                Some(NodeComponentInfo(nodeId.id, ComponentType.Source, "failOnNumber1SourceFactory")),
                SourceFailure,
                Context(input.contextId)
              )
            ).toValidatedNel
          } else {
            Valid(Context(input.contextId, Map("input" -> input.value), None))
          }
        }

    }

  }

  object SimpleSourceWithParameterTestingFactory extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke(returnType = classOf[SampleInputWithListAndMap])
    def create(): Source = new LiteSource[SampleInputWithListAndMap]
      with TestWithParametersSupport[SampleInputWithListAndMap]
      with ReturningType {
      override def returnType: typing.TypingResult = Typed[SampleInputWithListAndMap]

      override def createTransformation[F[_]: Monad](
          evaluateLazyParameter: CustomComponentContext[F]
      ): SampleInputWithListAndMap => ValidatedNel[ErrorType, Context] =
        input => Valid(Context(input.contextId, Map("input" -> input.asInstanceOf[Any]), None))

      override def testParametersDefinition: List[Parameter] = List(
        Parameter(ParameterName("contextId"), Typed.apply[String]),
        Parameter(
          ParameterName("numbers"),
          Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[java.lang.Long]))
        ),
        Parameter(
          ParameterName("additionalParams"),
          Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown))
        )
      )

      override def parametersToTestData(params: Map[ParameterName, AnyRef]): SampleInputWithListAndMap =
        SampleInputWithListAndMap(
          params(ParameterName("contextId")).asInstanceOf[String],
          params(ParameterName("numbers")).asInstanceOf[java.util.List[Long]],
          params(ParameterName("additionalParams")).asInstanceOf[java.util.Map[String, Any]]
        )

    }

  }

  object SimpleSinkFactory extends SinkFactory {

    @MethodToInvoke
    def create(@ParamName("value") value: LazyParameter[AnyRef]): LazyParamSink[AnyRef] = {
      new LazyParamSink[AnyRef] {
        override def prepareResponse: LazyParameter[AnyRef] = value
      }
    }

  }

}
