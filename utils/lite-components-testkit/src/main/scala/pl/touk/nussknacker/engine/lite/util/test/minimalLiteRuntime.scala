package pl.touk.nussknacker.engine.lite.util.test

import cats.{Id, Monad, catsInstancesForId}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape.transform
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.lite.api.commonTypes.ResultType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

/*
  Id based engine, suited for testing generic Lite components
 */
object minimalLiteRuntime {

  implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
  implicit val capabilityTransformer: CapabilityTransformer[Id] = new FixedCapabilityTransformer[Id]
  implicit val syncIdShape: InterpreterShape[Id] = new InterpreterShape[Id] {

    private val waitTime = 10 seconds

    override def monad: Monad[Id] = Monad[Id]

    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => Id[Either[T, Throwable]] = f => Await.result(transform(f), waitTime)
  }

  def run(modelData: ModelData,
          scenario: EspProcess,
          data: ScenarioInputBatch[Any],
          runtimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp): ResultType[EndResult[AnyRef]] = {

    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[Id, Any, AnyRef](scenario, modelData, Nil, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)
      .fold(errors => throw new IllegalArgumentException(errors.toString()), identity)
    interpreter.open(runtimeContextPreparer.prepare(JobData(scenario.metaData, ProcessVersion.empty)))
    try {
      val value: Id[ResultType[EndResult[AnyRef]]] = interpreter.invoke(data)
      value
    } finally {
      interpreter.close()
    }
  }

}
