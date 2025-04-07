package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.compile.EngineNodeDependencies

// It contains data available during job runtime - basically it is a combination of data passed from designer (JobData)
// and data enriched by runtime job (currently EngineNodeDependencies)
// FIXME abr: rename to Node(Scenario?)CompilationDependencies, and + extract NodeCompilationDependencies with NodeId
class JobRuntimeData(val jobData: JobData, engineNodeDependencies: EngineNodeDependencies) {
  def metaData: MetaData             = jobData.metaData
  def processVersion: ProcessVersion = jobData.processVersion

  lazy val nodeDependencies: List[NodeDependencyValue] =
    TypedNodeDependencyValue(metaData) :: engineNodeDependencies.dependencies

  implicit def implicitJobData: JobData = jobData
}
