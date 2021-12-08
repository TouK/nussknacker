package pl.touk.nussknacker.engine.lite

import cats.data.{State, StateT, ValidatedNel}
import cats.Monad
import cats.data.Validated.Valid
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.lite.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.{CapabilityTransformer, CustomComponentContext, LiteSource}
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.api.utils.transformers.ContextMappingComponent
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

/*
  This is sample engine, with simple state - a map of counters, and simple aggregation based on this state. Mainly for testing purposes
 */
object sample {

  implicit val shape: InterpreterShape[StateType] = new InterpreterShape[StateType] {

    import InterpreterShape._

    override def monad: Monad[StateType] = implicitly[Monad[StateType]]

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => StateType[Either[T, Throwable]] =
      f => StateT.pure(Await.result(transform(f)(ec), 1 second))

  }

  implicit val capabilityTransformer: CapabilityTransformer[StateType] = new FixedCapabilityTransformer[StateType]

  type StateType[M] = State[Map[String, Double], M]

  val modelData: LocalModelData = LocalModelData(ConfigFactory.empty(), StateConfigCreator)

  // TODO: Some smarter type in Input than Context?
  def run(scenario: EspProcess, data: ScenarioInputBatch[Context], initialState: Map[String, Double], runtimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp): ResultType[EndResult[AnyRef]] = {
    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[StateType, Context, AnyRef](scenario, modelData, Nil, ProductionServiceInvocationCollector, RunMode.Normal)
      .fold(k => throw new IllegalArgumentException(k.toString()), identity)
    interpreter.open(runtimeContextPreparer.prepare(JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)))
    interpreter.invoke(data).runA(initialState).value
  }

  class SumTransformer(name: String, outputVar: String, value: LazyParameter[java.lang.Double]) extends ContextMappingComponent {

    override def createStateTransformation[F[_]:Monad](context: CustomComponentContext[F]): Context => F[Context] = {
      val interpreter = context.interpreter.syncInterpretationFunction(value)
      val convert = context.capabilityTransformer.transform[StateType].getOrElse(throw new IllegalArgumentException("No capability!"))
      (ctx: Context) =>
        convert(State((current: Map[String, Double]) => {
          val newValue = current.getOrElse(name, 0D) + interpreter(ctx)
          (current + (name -> newValue), ctx.withVariable(outputVar, newValue))
        }))
    }
  }

  object StateConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map("sum" -> WithCategories(SumTransformerFactory))

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map("start" -> WithCategories(SimpleSourceFactory))


    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
      Map("failOnNumber1" -> WithCategories(FailOnNumber1))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SimpleSinkFactory))

  }

  object FailOnNumber1 extends Service {
    @MethodToInvoke
    def invoke(@ParamName("value") value: Integer): Future[Integer] =
      if (value == 1) Future.failed(new IllegalArgumentException("Should not happen :)")) else Future.successful(value)
  }

  object SumTransformerFactory extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[Double])
    def invoke(@ParamName("name") name: String,
               @ParamName("value") value: LazyParameter[java.lang.Double],
               @OutputVariableName outputVar: String) = new SumTransformer(name, outputVar, value)
  }

  object SimpleSourceFactory extends SourceFactory {

    @MethodToInvoke
    def create(): Source = new LiteSource[Context] {
      override def createTransformation[F[_] : Monad](evaluateLazyParameter: CustomComponentContext[F]): Context => ValidatedNel[ErrorType, Context] =
        ctx => Valid(ctx)
    }
  }

  object SimpleSinkFactory extends SinkFactory {
    @MethodToInvoke
    def create(@ParamName("value") value: LazyParameter[AnyRef]): LazyParamSink[AnyRef] = (_: LazyParameterInterpreter) => value
  }

}
