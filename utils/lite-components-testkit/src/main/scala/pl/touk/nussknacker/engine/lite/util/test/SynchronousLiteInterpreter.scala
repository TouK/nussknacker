package pl.touk.nussknacker.engine.lite.util.test

import cats.Id
import cats.data.{NonEmptyList, Validated}
<<<<<<< HEAD
import cats.{Id, Monad}
=======
>>>>>>> 2ccc1862ef (different solution)
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape.transform
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.lite.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds

/*
  Id based engine, suited for testing generic Lite components
 */
object SynchronousLiteInterpreter {

  type SynchronousResult = Validated[NonEmptyList[ProcessCompilationError], (List[ErrorType], List[EndResult[AnyRef]])]

  implicit val ec: ExecutionContext = SynchronousExecutionContextAndIORuntime.ctx
  implicit val capabilityTransformer: CapabilityTransformer[Id] = new FixedCapabilityTransformer[Id]
  implicit val syncIdShape: InterpreterShape[Id] = new InterpreterShape[Id] {

    private val waitTime = 10 seconds

<<<<<<< HEAD
    override def monad: Monad[Id] = Monad[Id]

    override def fromFuture[T]: Future[T] => Id[Either[T, Throwable]] = f => Await.result(transform(f), waitTime)
=======
    override def fromFuture[T](implicit ec: ExecutionContext): Future[T] => Id[Either[T, Throwable]] = f => Await.result(transform(f), waitTime)
>>>>>>> 2ccc1862ef (different solution)
  }
  // TODO: add generate test data support

  def run(modelData: ModelData,
          scenario: CanonicalProcess,
          data: ScenarioInputBatch[Any],
          componentUseCase: ComponentUseCase,
          runtimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp): SynchronousResult = {

    ScenarioInterpreterFactory
      .createInterpreter[Id, Any, AnyRef](scenario, modelData, Nil, ProductionServiceInvocationCollector, componentUseCase)
      .map { interpreter =>
        interpreter.open(runtimeContextPreparer.prepare(JobData(scenario.metaData, ProcessVersion.empty)))
        try {
          val value: Id[ResultType[EndResult[AnyRef]]] = interpreter.invoke(data)
          value.run
        } finally {
          interpreter.close()
        }
      }
  }

}
