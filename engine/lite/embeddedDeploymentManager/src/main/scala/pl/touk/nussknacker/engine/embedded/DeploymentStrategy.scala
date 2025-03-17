package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.BaseModelDataProvider
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer

import scala.concurrent.ExecutionContext
import scala.util.Try

// TODO: replace with RunnableScenarioInterpreterFactory usage
trait DeploymentStrategy {

  protected var contextPreparer: LiteEngineRuntimeContextPreparer = _
  protected var modelDataProvider: BaseModelDataProvider          = _

  def open(modelDataProvider: BaseModelDataProvider, contextPreparer: LiteEngineRuntimeContextPreparer): Unit = {
    this.modelDataProvider = modelDataProvider
    this.contextPreparer = contextPreparer
  }

  def close(): Unit

  def onScenarioAdded(
      jobData: JobData,
      nodesDeploymentData: NodesDeploymentData,
      parsedResolvedScenario: CanonicalProcess
  )(
      implicit ec: ExecutionContext
  ): Try[Deployment]

}

trait Deployment extends AutoCloseable {

  def status(): DeploymentStatus

}
