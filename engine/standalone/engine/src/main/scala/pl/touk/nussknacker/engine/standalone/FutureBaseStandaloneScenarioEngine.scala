package pl.touk.nussknacker.engine.standalone

import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.{EffectUnwrapper, TestRunner}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object FutureBaseStandaloneScenarioEngine {

  type InterpreterType = StandaloneScenarioEngine.StandaloneScenarioInterpreter[Future]

  implicit val cap: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()
  implicit val unwrapper: EffectUnwrapper[Future] = new EffectUnwrapper[Future] {
    override def unwrap[Y](eff: Future[Y]): Y = Await.result(eff, 10 seconds)
  }
  implicit def interpreterShape(implicit ec: ExecutionContext): FutureShape = new FutureShape()

  def testRunner(implicit ec: ExecutionContext): TestRunner[Future, AnyRef] = {
    StandaloneScenarioEngine.testRunner[Future]
  }

}
