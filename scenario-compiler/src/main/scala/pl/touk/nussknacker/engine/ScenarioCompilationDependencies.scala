package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.EngineNodeCompilationDependencies

// It contains data necessary to compile scenario. Basically it is a combination of data passed from designer (JobData)
// and data enriched by job in runtime (currently EngineNodeCompilationDependencies)
class ScenarioCompilationDependencies(
    val jobData: JobData,
    engineNodeCompilationDependencies: EngineNodeCompilationDependencies
) {
  def metaData: MetaData             = jobData.metaData
  def processVersion: ProcessVersion = jobData.processVersion

  lazy val nodeDependencies: List[NodeDependencyValue] =
    TypedNodeDependencyValue(metaData) :: engineNodeCompilationDependencies.nodeCompilationDependencies

  implicit def implicitJobData: JobData = jobData
}
