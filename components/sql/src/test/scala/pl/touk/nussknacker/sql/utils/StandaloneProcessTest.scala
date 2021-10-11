package pl.touk.nussknacker.sql.utils

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.GenericListResultType
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.RuntimeContextPreparer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.standalone.StandaloneScenarioEngine
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

trait StandaloneProcessTest extends Matchers with ScalaFutures {

  val runMode: RunMode = RunMode.Test

  def modelData: LocalModelData

  def contextPreparer: RuntimeContextPreparer

  def runProcess(process: EspProcess, input: Any): GenericListResultType[Any] = {
    val interpreter = prepareInterpreter(process)
    interpreter.open(JobData(process.metaData, ProcessVersion.empty, DeploymentData.empty))
    try {
      interpreter.invoke(input, None).futureValue
    } finally {
      interpreter.close()
    }
  }

  private def prepareInterpreter(process: EspProcess): StandaloneScenarioEngine.StandaloneScenarioInterpreter = {
    val validatedInterpreter = StandaloneScenarioEngine(process, contextPreparer, modelData, Nil, ProductionServiceInvocationCollector, runMode)

    validatedInterpreter shouldBe 'valid
    validatedInterpreter.toEither.right.get
  }
}
