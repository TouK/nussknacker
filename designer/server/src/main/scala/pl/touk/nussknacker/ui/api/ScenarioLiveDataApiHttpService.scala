package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, LiveDataPreviewSupported, NoLiveDataPreviewSupport}
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.LiveData
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.LiveDataError.NoLiveDataAvailableForScenario
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.description.scenarioLiveData.Dtos._
import pl.touk.nussknacker.ui.api.description.scenarioLiveData.Dtos.LiveDataError._
import pl.touk.nussknacker.ui.api.description.scenarioLiveData.ScenarioLiveDataApiEndpoints
import pl.touk.nussknacker.ui.api.description.scenarioTesting.ResultsWithCountsDto
import pl.touk.nussknacker.ui.api.utils.ScenarioHttpServiceExtensions
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

class ScenarioLiveDataApiHttpService(
    authManager: AuthManager,
    scenarioAuthorizer: AuthorizeProcess,
    dmDispatcher: DeploymentManagerDispatcher,
    protected override val scenarioService: ProcessService,
    processCounter: ProcessCounter,
)(override protected implicit val executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with ScenarioHttpServiceExtensions
    with LazyLogging {

  override protected type BusinessErrorType = LiveDataError
  override protected def noScenarioError(scenarioName: ProcessName): LiveDataError      = NoScenario
  override protected def noPermissionError: LiveDataError with CustomAuthorizationError = NoPermission

  private val endpoints = new ScenarioLiveDataApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    endpoints.scenarioLiveDataEndpoint
      .serverSecurityLogic(authorizeKnownUser[LiveDataError])
      .serverLogicEitherT { implicit loggedUser => scenarioName =>
        for {
          scenarioWithDetails <- getScenarioWithDetailsByName(
            scenarioName,
            GetScenarioWithDetailsOptions.withScenarioGraph
          )
          processIdWithName = ProcessIdWithName(scenarioWithDetails.processIdUnsafe, scenarioName)
          _ <- isAuthorized(processIdWithName.id, Permission.Read)
          deploymentManager <- EitherT[Future, LiveDataError, DeploymentManager] {
            dmDispatcher.deploymentManager(processIdWithName).map {
              case Some(deploymentManager) => Right(deploymentManager)
              case None                    => Left(NoScenario)
            }
          }
          liveData <- EitherT[Future, LiveDataError, LiveData] {
            deploymentManager.liveDataPreviewSupport match {
              case supported: LiveDataPreviewSupported =>
                supported.getLiveData(processIdWithName).map {
                  case Right(liveData)                      => Right(liveData)
                  case Left(NoLiveDataAvailableForScenario) => Left(LiveDataNotAvailable)
                }
              case NoLiveDataPreviewSupport =>
                Future.successful(Left(LiveDataNotSupported))
            }
          }
        } yield ResultsWithCountsDto.from(liveData, computeCounts(scenarioWithDetails, liveData))
      }
  }

  private def computeCounts(scenarioWithDetails: ScenarioWithDetails, liveData: LiveData)(
      implicit loggedUser: LoggedUser
  ): Map[String, NodeCount] = {
    val incomingCounts =
      liveData.nodeTransitions.toList
        .flatMap { case (transition, data) => transition.destinationNodeId.map(_ -> data.totalCount) }
        .toGroupedMap
        .mapValuesNow(_.sum)

    val outgoingCounts =
      liveData.nodeTransitions.toList
        .map { case (transition, data) => transition.sourceNodeId -> data.totalCount }
        .toGroupedMap
        .mapValuesNow(_.sum)

    def getCount(nodeId: String): Option[RawCount] = Some(
      RawCount(
        incomingCounts
          .get(nodeId)
          .orElse(outgoingCounts.get(nodeId))
          .getOrElse(0),
        liveData.exceptions.getOrElse(NodeId(nodeId), List.empty).size.toLong
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(
      scenarioWithDetails.scenarioGraphUnsafe,
      scenarioWithDetails.processVersionUnsafe.processName
    )

    processCounter.computeCounts(canonical, scenarioWithDetails.isFragment, getCount)
  }

  private def isAuthorized(scenarioId: ProcessId, permission: Permission)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, LiveDataError, Unit] =
    EitherT(
      scenarioAuthorizer
        .check(scenarioId, permission, loggedUser)
        .map[Either[LiveDataError, Unit]] {
          case true  => Right(())
          case false => Left(noPermissionError)
        }
    )

}
