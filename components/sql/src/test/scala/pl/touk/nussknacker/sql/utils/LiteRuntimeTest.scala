package pl.touk.nussknacker.sql.utils

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter.RequestResponseResultType
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

import scala.concurrent.Future

trait LiteRuntimeTest extends Matchers with ScalaFutures {

  val componentUseCase: ComponentUseCase = ComponentUseCase.TestRuntime

  def modelData: LocalModelData

  def contextPreparer: LiteEngineRuntimeContextPreparer

  def runProcess(process: EspProcess, input: Any): RequestResponseResultType[List[Any]] = {
    val interpreter = prepareInterpreter(process)
    interpreter.open()
    try {
      interpreter.invokeToOutput(input).futureValue
    } finally {
      interpreter.close()
    }
  }

  private def prepareInterpreter(process: EspProcess): InterpreterType = {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    val validatedInterpreter = RequestResponseInterpreter[Future](process,
      ProcessVersion.empty, 
      contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, componentUseCase)

    validatedInterpreter shouldBe 'valid
    validatedInterpreter.toEither.right.get
  }
}
