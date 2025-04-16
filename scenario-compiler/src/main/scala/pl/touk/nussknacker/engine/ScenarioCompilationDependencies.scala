package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.{JobData, MetaData}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies

// It contains data necessary to compile scenario. Basically it is a combination of data passed from designer (JobData)
// and data enriched by job in runtime (currently EngineScenarioCompilationDependencies)
class ScenarioCompilationDependencies(
    val jobData: JobData,
    engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies
) {
  def metaData: MetaData = jobData.metaData

  lazy val nodeDependencies: List[NodeDependencyValue] =
    TypedNodeDependencyValue(jobData.metaData) :: engineScenarioCompilationDependencies.nodeCompilationDependencies

  implicit def implicitJobData: JobData = jobData
}
