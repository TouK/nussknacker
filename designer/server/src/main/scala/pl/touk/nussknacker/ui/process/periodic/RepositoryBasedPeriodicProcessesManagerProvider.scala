package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.periodic.{PeriodicProcessesManager, PeriodicProcessesManagerProvider}
import pl.touk.nussknacker.ui.process.repository.PeriodicProcessesRepository

class RepositoryBasedPeriodicProcessesManagerProvider(
    periodicProcessesRepository: PeriodicProcessesRepository,
) extends PeriodicProcessesManagerProvider {

  override def provide(
      deploymentManagerName: String,
      processingType: String
  ): PeriodicProcessesManager = {
    new RepositoryBasedPeriodicProcessesManager(
      deploymentManagerName,
      processingType,
      periodicProcessesRepository
    )
  }

}
