package pl.touk.nussknacker.engine.baseengine

import cats.data.StateT
import cats.{Monad, MonadError}
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{BaseEngineSink, CustomTransformerContext, ResultType, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.RuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.api.utils.transformers.MapWithStateCustomTransformer
import pl.touk.nussknacker.engine.baseengine.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/*
  This is sample engine, with simple state - a map of counters, and simple aggregation based on this state. Mainly for testing purposes
 */
object sample {

  implicit val shape: InterpreterShape[StateType] = new InterpreterShape[StateType] {

    override def monadError: MonadError[StateType, Throwable] = implicitly[MonadError[StateType, Throwable]]

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => StateType[T] = f => StateT.pure(Await.result(f, 1 second))

  }

  //TODO: errors in interpreter should be handled as List[ErrorType], we should get rid of MonadError
  type StateType[M] = StateT[Try, Map[String, Double], M]

  val modelData: LocalModelData = LocalModelData(ConfigFactory.empty(), StateConfigCreator)
  val runtimeContextPreparer = new RuntimeContextPreparer(NoOpMetricsProvider)

  def run(scenario: EspProcess, data: List[(SourceId, Context)], initialState: Map[String, Double]): ResultType[BaseScenarioEngineTypes.EndResult[AnyRef]] = {
    val interpreter = StateEngine
      .createInterpreter(scenario, runtimeContextPreparer, modelData, Nil, ProductionServiceInvocationCollector, RunMode.Normal)
      .fold(k => throw new IllegalArgumentException(k.toString()), identity)
    interpreter.invoke(data).runA(initialState).get
  }

  class SumTransformer(name: String, outputVar: String, value: LazyParameter[java.lang.Double]) extends MapWithStateCustomTransformer[StateType] {

    val monad: Monad[StateType] = implicitly[Monad[StateType]]

    override def createStateTransformation(context: CustomTransformerContext): Context => StateType[Context] = {
      val interpreter = context.syncInterpretationFunction(value)
      (ctx: Context) =>
        StateT((current: Map[String, Double]) => {
          val newValue = current.getOrElse(name, 0D) + interpreter(ctx)
          Try((current + (name -> newValue), ctx.withVariable(outputVar, newValue)))
        })
    }
  }

  object StateEngine extends BaseScenarioEngine[StateType, AnyRef]

  object StateConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map("sum" -> WithCategories(SumTransformerFactory))

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] =
      Map("start" -> WithCategories(SimpleSourceFactory))


    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
      Map("failOnAla" -> WithCategories(FailOnAla))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      Map("end" -> WithCategories(SimpleSinkFactory))

  }

  object FailOnAla extends Service {
    @MethodToInvoke
    def invoke(@ParamName("value") value: String): Future[String] =
      if (value == "ala") Future.failed(new IllegalArgumentException("Should not happen :)")) else Future.successful(value)
  }

  object SumTransformerFactory extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[Double])
    def invoke(@ParamName("name") name: String,
               @ParamName("value") value: LazyParameter[java.lang.Double],
               @OutputVariableName outputVar: String) = new SumTransformer(name, outputVar, value)
  }

  object SimpleSourceFactory extends SourceFactory[String] {
    override def clazz: Class[_] = classOf[String]

    @MethodToInvoke
    def create(): Source[String] = new Source[String] {}
  }

  object SimpleSinkFactory extends SinkFactory {
    @MethodToInvoke
    def create(@ParamName("value") value: LazyParameter[AnyRef]): BaseEngineSink[AnyRef] = (_: CustomTransformerContext) => value
  }

}
