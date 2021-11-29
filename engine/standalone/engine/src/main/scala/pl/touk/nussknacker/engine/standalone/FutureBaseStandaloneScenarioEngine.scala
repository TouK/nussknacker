package pl.touk.nussknacker.engine.standalone

import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.baseengine.TestRunner
import pl.touk.nussknacker.engine.baseengine.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer

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
