package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.periodic.{PeriodicProcessesManager, PeriodicProcessesManagerProvider}
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, PeriodicProcessesRepository}

import scala.concurrent.Future

class RepositoryBasedPeriodicProcessesManagerProvider(
    periodicProcessesRepository: PeriodicProcessesRepository,
    fetchingProcessRepository: FetchingProcessRepository[Future],
) extends PeriodicProcessesManagerProvider {

  override def provide(
      processingType: String
  ): PeriodicProcessesManager = {
    new RepositoryBasedPeriodicProcessesManager(
      processingType,
      periodicProcessesRepository,
      fetchingProcessRepository,
    )
  }

}
