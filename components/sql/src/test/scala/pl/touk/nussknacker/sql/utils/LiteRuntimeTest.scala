package pl.touk.nussknacker.sql.utils

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseResultType
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

import scala.concurrent.Future

//todo this is basically requestResponse runtime test
trait LiteRuntimeTest extends Matchers with ScalaFutures {

  val componentUseCase: ComponentUseCase = ComponentUseCase.TestRuntime

  def modelData: LocalModelData

  def contextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp

  def runProcess(process: CanonicalProcess, input: Any): RequestResponseResultType[List[Any]] = {
    val interpreter = prepareInterpreter(process)
    interpreter.open()
    try {
      interpreter.invokeToOutput(input).futureValue
    } finally {
      interpreter.close()
    }
  }

  private def prepareInterpreter(process: CanonicalProcess): InterpreterType = {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    val validatedInterpreter = RequestResponseInterpreter[Future](process,
      ProcessVersion.empty, 
      contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, componentUseCase)

    validatedInterpreter shouldBe Symbol("valid")
    validatedInterpreter.toEither.toOption.get
  }
}
