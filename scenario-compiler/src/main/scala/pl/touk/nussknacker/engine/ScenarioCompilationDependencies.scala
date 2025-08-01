package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.{JobData, MetaData}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies

// It contains data necessary to compile scenario. Basically it is a combination of data passed from designer (JobData)
// and data enriched by job in runtime (currently EngineScenarioCompilationDependencies)
class ScenarioCompilationDependencies(
    val jobData: JobData,
    val engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies
) {
  def metaData: MetaData = jobData.metaData

  implicit def implicitJobData: JobData = jobData
}
