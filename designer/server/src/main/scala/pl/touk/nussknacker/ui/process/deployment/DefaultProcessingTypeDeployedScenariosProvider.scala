package pl.touk.nussknacker.ui.process.deployment

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{AdditionalModelConfigs, DeploymentData, DeploymentId, User}
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// This class is extracted from DeploymentService to avoid a cyclic dependency:
// DeploymentService -> EmbeddedDeploymentManager -> ... -> DeploymentService.getDeployedScenarios
class DefaultProcessingTypeDeployedScenariosProvider(
    processRepository: FetchingProcessRepository[DB],
    dbioRunner: DBIOActionRunner,
    scenarioResolver: ScenarioResolver,
    processingType: ProcessingType
) extends ProcessingTypeDeployedScenariosProvider
    with LazyLogging {

  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] = {
    implicit val userFetchingDataFromRepository: LoggedUser = NussknackerInternalUser.instance
    for {
      deployedProcesses <- {
        dbioRunner.run(
          processRepository.fetchLatestProcessesDetails[CanonicalProcess](
            ScenarioQuery(
              isFragment = Some(false),
              isArchived = Some(false),
              isDeployed = Some(true),
              processingTypes = Some(Seq(processingType))
            )
          )
        )
      }
      dataList <- Future
        .sequence(deployedProcesses.map { details =>
          val lastDeployAction = details.lastDeployedAction.get
          // TODO: what should be in name?
          val deployingUser = User(lastDeployAction.user, lastDeployAction.user)
          val deploymentData = DeploymentData(
            DeploymentId.fromActionId(lastDeployAction.id),
            deployingUser,
            // TODO: Store this data and use them during jobs recovery. Currently after restart some jobs will work differently
            nodesData = NodesDeploymentData.empty,
            additionalDeploymentData = Map.empty,
            additionalModelConfigs = AdditionalModelConfigs.empty
          )
          scenarioResolver
            .resolveScenario(details.json)
            .map {
              case Validated.Valid(resolvedScenario) =>
                Some(
                  DeployedScenarioData(
                    details.toEngineProcessVersion.copy(versionId = lastDeployAction.processVersionId),
                    deploymentData,
                    resolvedScenario
                  )
                )
              case Validated.Invalid(errors) =>
                logger.error(
                  s"Cannot resolve deployed scenario ${details.name} with errors: " +
                    s"${errors.map(PrettyValidationErrors.formatErrorMessage)}"
                )
                None
            }
        })
        .map(_.flatten)
    } yield dataList
  }

}

object DefaultProcessingTypeDeployedScenariosProvider {

  // This factory method prepare objects that are also prepared by PekkoHttpBasedRouteProvider
  // but without dependency to ModelData - it necessary to avoid a cyclic dependency:
  // DeploymentService -> EmbeddedDeploymentManager -> ... -> DeploymentService.getDeployedScenarios
  def apply(dbRef: DbRef, processingType: ProcessingType)(
      implicit ec: ExecutionContext
  ): ProcessingTypeDeployedScenariosProvider = {
    val dbioRunner               = DBIOActionRunner(dbRef)
    val actionRepository         = DbScenarioActionRepository.create(dbRef)
    val scenarioLabelsRepository = new ScenarioLabelsRepository(dbRef)
    val processRepository        = DBFetchingProcessRepository.create(dbRef, actionRepository, scenarioLabelsRepository)
    val futureProcessRepository =
      DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository, scenarioLabelsRepository)
    new DefaultProcessingTypeDeployedScenariosProvider(
      processRepository,
      dbioRunner,
      new ScenarioResolver(
        new FragmentResolver(new DefaultFragmentRepository(futureProcessRepository)),
        processingType
      ),
      processingType
    )
  }

}
