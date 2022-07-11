package pl.touk.nussknacker.engine.embedded

import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer

import scala.concurrent.ExecutionContext
import scala.util.Try

trait DeploymentStrategy {

  protected var contextPreparer: LiteEngineRuntimeContextPreparer = _
  protected var modelData: ModelData = _
  protected var metricRegistry: MetricRegistry = _


  def open(modelData: ModelData, contextPreparer: LiteEngineRuntimeContextPreparer, metricRegistry: MetricRegistry): Unit = {
    this.modelData = modelData
    this.contextPreparer = contextPreparer
    this.metricRegistry = metricRegistry
  }

  def close(): Unit

  def onScenarioAdded(jobData: JobData,
                     parsedResolvedScenario: EspProcess)(implicit ec: ExecutionContext): Try[Deployment]
  
  def testRunner(implicit ec: ExecutionContext): TestRunner

}

trait Deployment extends AutoCloseable {

  def status(): StateStatus

}