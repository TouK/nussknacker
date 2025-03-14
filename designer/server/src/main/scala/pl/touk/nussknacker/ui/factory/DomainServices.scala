package pl.touk.nussknacker.ui.factory

import cats.effect.IO
import pl.touk.nussknacker.processCounts.CountsReporter
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ScenarioStatusPresenter}
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.notifications.Notification
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.deployment.{ActionService, DeploymentManagerDispatcher}
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.processingtype.{CombinedProcessingTypeData, ProcessingTypeServices}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.{
  FetchingProcessRepository,
  ScenarioActionRepository,
  ScenarioLabelsRepository
}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.statistics.FingerprintService
import pl.touk.nussknacker.ui.util.InMemoryTimeseriesRepository

import scala.concurrent.Future

final case class DomainServices(
    futureProcessRepository: FetchingProcessRepository[Future],
    scenarioActivityRepository: ScenarioActivityRepository,
    scenarioLabelsRepository: ScenarioLabelsRepository,
    globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
    feStatisticsRepository: FEStatisticsRepository[Future],
    componentService: ComponentService,
    processService: ProcessService,
    fetchScenarioActivityService: FetchScenarioActivityService,
    actionRepository: ScenarioActionRepository,
    deploymentRepository: DeploymentRepository,
    processChangeListener: ProcessChangeListener,
    actionService: ActionService,
    countsReporter: Option[CountsReporter[Future]],
    counter: ProcessCounter,
    fingerprintService: FingerprintService,
    scenarioStatusProvider: ScenarioStatusProvider,
    scenarioStatusPresenter: ScenarioStatusPresenter,
    dmDispatcher: DeploymentManagerDispatcher,
    processingTypeServicesProvider: ProcessingTypeDataProvider[ProcessingTypeServices, CombinedProcessingTypeData],
    reloadProcessingTypes: IO[Unit],
    processAuthorizer: AuthorizeProcess,
)
