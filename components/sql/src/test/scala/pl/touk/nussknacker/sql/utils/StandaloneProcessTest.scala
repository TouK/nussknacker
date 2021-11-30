package pl.touk.nussknacker.sql.utils

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine
import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine.RequestResponseResultType
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

import scala.concurrent.Future

trait StandaloneProcessTest extends Matchers with ScalaFutures {

  val runMode: RunMode = RunMode.Test

  def modelData: LocalModelData

  def contextPreparer: LiteEngineRuntimeContextPreparer

  def runProcess(process: EspProcess, input: Any): RequestResponseResultType[List[Any]] = {
    val interpreter = prepareInterpreter(process)
    interpreter.open()
    try {
      interpreter.invokeToOutput(input, None).futureValue
    } finally {
      interpreter.close()
    }
  }

  private def prepareInterpreter(process: EspProcess): InterpreterType = {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    val validatedInterpreter = RequestResponseEngine[Future](process,
      ProcessVersion.empty, DeploymentData.empty,
      contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, runMode)

    validatedInterpreter shouldBe 'valid
    validatedInterpreter.toEither.right.get
  }
}
