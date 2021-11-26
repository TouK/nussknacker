package pl.touk.nussknacker.engine.standalone

import cats.Monad
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.baseengine.TestRunner
import pl.touk.nussknacker.engine.baseengine.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.CapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object FutureBaseStandaloneScenarioEngine {

  type InterpreterType = StandaloneScenarioEngine.StandaloneScenarioInterpreter[Future]
  private val scenarioTimeout = 10 seconds

  implicit val cap: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  implicit val unwrapper: EffectUnwrapper[Future] = new EffectUnwrapper[Future] {
    override def apply[Y](eff: Future[Y]): Y = Await.result(eff, scenarioTimeout)
  }

  implicit def interpreterShape(implicit ec: ExecutionContext): FutureShape = new FutureShape()

  def testRunner(implicit ec: ExecutionContext): TestRunner[Future, AnyRef] = {
    StandaloneScenarioEngine.testRunner[Future]
  }

}
