package pl.touk.nussknacker.engine.embedded

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer

import scala.concurrent.ExecutionContext
import scala.util.Try

trait DeploymentStrategy {

  type ScenarioInterpreter

  def open(): Unit

  def close(): Unit

  def onScenarioAdded(jobData: JobData,
                       modelData: ModelData,
                       parsedResolvedScenario: EspProcess,
                       contextPreparer: LiteEngineRuntimeContextPreparer)(implicit ec: ExecutionContext): Try[ScenarioInterpreter]

  def onScenarioCancelled(data: ScenarioInterpreter): Unit

  def readStatus(data: ScenarioInterpreter): StateStatus

  def testRunner(implicit ec: ExecutionContext): TestRunner

}
