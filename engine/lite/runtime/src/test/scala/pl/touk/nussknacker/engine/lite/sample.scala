package pl.touk.nussknacker.engine.lite

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data.{State, StateT, ValidatedNel}
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.WithCategories.anyCategory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
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
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
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

    override def monad: Monad[StateType] = implicitly[Monad[StateType]]

    override def fromFuture[T]: Future[T] => StateType[Either[T, Throwable]] =
      f => StateT.pure(Await.result(transform(f), 1 second))

  }

  implicit val capabilityTransformer: CapabilityTransformer[StateType] = new FixedCapabilityTransformer[StateType]

  type StateType[M] = State[Map[String, Double], M]

  val modelData: LocalModelData = LocalModelData(ConfigFactory.empty(), StateConfigCreator)

  def run(
      scenario: CanonicalProcess,
      data: ScenarioInputBatch[SampleInput],
      initialState: Map[String, Double],
      runtimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp
  ): ResultType[EndResult[AnyRef]] = {
    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[StateType, SampleInput, AnyRef](
        scenario,
        modelData,
        Nil,
        ProductionServiceInvocationCollector,
        ComponentUseCase.EngineRuntime
      )
      .fold(k => throw new IllegalArgumentException(k.toString()), identity)
    interpreter.open(runtimeContextPreparer.prepare(JobData(scenario.metaData, ProcessVersion.empty)))
    val interpreterResult      = interpreter.invoke(data)
    val resultWithInitialState = interpreterResult.runA(initialState).value
    resultWithInitialState
  }

  def test(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Any] = {
    implicit val effectUnwrapper: EffectUnwrapper[StateType] = new EffectUnwrapper[StateType] {
      override def apply[A](fa: StateType[A]): A = fa.runA(Map.empty).value
    }
    val testRunner = new InterpreterTestRunner[StateType, SampleInput, AnyRef]
    testRunner.runTest(modelData, scenarioTestData, scenario, identity)
  }

  class SumTransformer(name: String, outputVar: String, value: LazyParameter[java.lang.Double])
      extends ContextMappingComponent {

    override def createStateTransformation[F[_]: Monad](context: CustomComponentContext[F]): Context => F[Context] = {
      val interpreter = context.interpreter.syncInterpretationFunction(value)
      val convert = context.capabilityTransformer
        .transform[StateType]
        .getOrElse(throw new IllegalArgumentException("No capability!"))
      (ctx: Context) =>
        convert(State((current: Map[String, Double]) => {
          val newValue = current.getOrElse(name, 0d) + interpreter(ctx)
          (current + (name -> newValue), ctx.withVariable(outputVar, newValue))
        }))
    }

  }

  class UtilHelpers {
    def largestListElement(list: java.util.List[Long]): Long = list.asScala.max
  }

  object StateConfigCreator extends EmptyProcessConfigCreator {

    override def customStreamTransformers(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[CustomStreamTransformer]] =
      Map("sum" -> WithCategories(SumTransformerFactory))

    override def sourceFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] =
      Map(
        "start"               -> WithCategories(SimpleSourceFactory),
        "parametersSupport"   -> WithCategories(SimpleSourceWithParameterTestingFactory),
        "failOnNumber1Source" -> WithCategories(FailOnNumber1SourceFactory)
      )

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
      Map(
        "failOnNumber1" -> WithCategories(FailOnNumber1),
        "noOpProcessor" -> WithCategories(NoOpProcessor),
        "sumNumbers"    -> WithCategories(SumNumbers),
      )

    override def sinkFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SimpleSinkFactory))

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig =
      ExpressionConfig(
        Map("UTIL" -> anyCategory(new UtilHelpers)),
        List()
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

  object SimpleSourceFactory extends SourceFactory {

    @MethodToInvoke
    def create(): Source = new LiteSource[SampleInput] with SourceTestSupport[SampleInput] {

      override def createTransformation[F[_]: Monad](
          evaluateLazyParameter: CustomComponentContext[F]
      ): SampleInput => ValidatedNel[ErrorType, Context] =
        input => Valid(Context(input.contextId, Map("input" -> input.value), None))

      override def testRecordParser: TestRecordParser[SampleInput] = (testRecord: TestRecord) => {
        val fields = CirceUtil.decodeJsonUnsafe[String](testRecord.json).split("\\|")
        SampleInput(fields(0), fields(1).toInt)
      }

    }

  }

  object FailOnNumber1SourceFactory extends SourceFactory {

    @MethodToInvoke
    def create()(implicit nodeId: NodeId): Source = new LiteSource[SampleInput] {

      override def createTransformation[F[_]: Monad](
          evaluateLazyParameter: CustomComponentContext[F]
      ): SampleInput => ValidatedNel[ErrorType, Context] =
        input => {
          if (input.value == 1) {
            Invalid(
              NuExceptionInfo(
                Some(NodeComponentInfo(nodeId.id, "failOnNumber1SourceFactory", ComponentType.Source)),
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

  object SimpleSourceWithParameterTestingFactory extends SourceFactory {

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
        Parameter("contextId", Typed.apply[String]),
        Parameter("numbers", Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed[java.lang.Long]))),
        Parameter("additionalParams", Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown)))
      )

      override def parametersToTestData(params: Map[String, AnyRef]): SampleInputWithListAndMap =
        SampleInputWithListAndMap(
          params("contextId").asInstanceOf[String],
          params("numbers").asInstanceOf[java.util.List[Long]],
          params("additionalParams").asInstanceOf[java.util.Map[String, Any]]
        )

    }

  }

  object SimpleSinkFactory extends SinkFactory {

    @MethodToInvoke
    def create(@ParamName("value") value: LazyParameter[AnyRef]): LazyParamSink[AnyRef] =
      (_: LazyParameterInterpreter) => value

  }

}
