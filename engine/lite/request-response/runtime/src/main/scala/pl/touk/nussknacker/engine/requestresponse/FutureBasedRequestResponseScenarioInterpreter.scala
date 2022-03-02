package pl.touk.nussknacker.engine.requestresponse

import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.TestRunner._
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer

import scala.concurrent.{ExecutionContext, Future}

object FutureBasedRequestResponseScenarioInterpreter {

  type InterpreterType = RequestResponseInterpreter.RequestResponseScenarioInterpreter[Future]

  implicit val cap: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  implicit def interpreterShape(implicit ec: ExecutionContext): FutureShape = new FutureShape()

  def testRunner(implicit ec: ExecutionContext): TestRunner = RequestResponseInterpreter.testRunner[Future]

}
