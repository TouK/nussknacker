package pl.touk.nussknacker.sql.utils

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.standalone.api.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.api.types.GenericListResultType
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx


trait StandaloneProcessTest extends Matchers with ScalaFutures {

  def modelData: LocalModelData
  def contextPreparer: StandaloneContextPreparer

  def runProcess(process: EspProcess, input: Any): GenericListResultType[Any] = {
    val interpreter = prepareInterpreter(process)
    interpreter.open(JobData(process.metaData, ProcessVersion.empty, DeploymentData.empty))
    try {
      interpreter.invoke(input, None).futureValue
    } finally {
      interpreter.close()
    }
  }

  private def prepareInterpreter(process: EspProcess): StandaloneProcessInterpreter = {
    val validatedInterpreter = StandaloneProcessInterpreter(process, contextPreparer, modelData, Nil, ProductionServiceInvocationCollector)

    validatedInterpreter shouldBe 'valid
    validatedInterpreter.toEither.right.get
  }
}
