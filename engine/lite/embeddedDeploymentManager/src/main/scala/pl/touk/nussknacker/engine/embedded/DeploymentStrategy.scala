package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer

import scala.concurrent.ExecutionContext
import scala.util.Try

// TODO: replace with RunnableScenarioInterpreterFactory usage
trait DeploymentStrategy {

  protected var contextPreparer: LiteEngineRuntimeContextPreparer = _
  protected var modelData: ModelData                              = _

  def open(modelData: ModelData, contextPreparer: LiteEngineRuntimeContextPreparer): Unit = {
    this.modelData = modelData
    this.contextPreparer = contextPreparer
  }

  def close(): Unit

  def onScenarioAdded(jobData: JobData, parsedResolvedScenario: CanonicalProcess)(
      implicit ec: ExecutionContext
  ): Try[Deployment]

}

trait Deployment extends AutoCloseable {

  def status(): DeploymentStatus

}
