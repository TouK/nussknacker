package pl.touk.nussknacker.engine.requestresponse

import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.TestRunner.EffectUnwrapper
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object FutureBasedRequestResponseScenarioInterpreter {

  type InterpreterType = RequestResponseEngine.RequestResponseScenarioInterpreter[Future]

  private val scenarioTimeout = 10 seconds

  implicit val cap: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  //TODO: should we consider configurable timeout?
  implicit val unwrapper: EffectUnwrapper[Future] = new EffectUnwrapper[Future] {
    override def apply[Y](eff: Future[Y]): Y = Await.result(eff, scenarioTimeout)
  }

  implicit def interpreterShape(implicit ec: ExecutionContext): FutureShape = new FutureShape()

  def testRunner(implicit ec: ExecutionContext): TestRunner[Future, AnyRef] = {
    RequestResponseEngine.testRunner[Future]
  }


}
