package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessingType, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.process.EnrichedWithLastNonTechnicalEditionProcessesWithDetailsProvider.{
  TechnicalUsers,
  fetchLatestNonTechnicalModificationHeaderName
}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait ProcessesWithDetailsProvider {

  def getLatestProcessWithDetails(
      rawQueryParams: Map[String, String],
      processId: ProcessIdWithName,
      options: GetScenarioWithDetailsOptions
  )(implicit user: LoggedUser): Future[ScenarioWithDetails]

  def getLatestProcessesWithDetails(
      rawQueryParams: Map[String, String],
      query: ScenarioQuery,
      options: GetScenarioWithDetailsOptions
  )(implicit user: LoggedUser): Future[List[ScenarioWithDetails]]

}

class ServiceBasedProcessesWithDetailsProvider(
    service: ProcessService
) extends ProcessesWithDetailsProvider {

  override def getLatestProcessWithDetails(
      rawQueryParams: Map[String, String],
      processId: ProcessIdWithName,
      options: GetScenarioWithDetailsOptions
  )(implicit user: LoggedUser): Future[ScenarioWithDetails] =
    service.getLatestProcessWithDetails(processId, options)

  override def getLatestProcessesWithDetails(
      rawQueryParams: Map[String, String],
      query: ScenarioQuery,
      options: GetScenarioWithDetailsOptions
  )(implicit user: LoggedUser): Future[List[ScenarioWithDetails]] =
    service.getLatestProcessesWithDetails(query, options)

}

class EnrichedWithLastNonTechnicalEditionProcessesWithDetailsProvider(
    underlying: ProcessesWithDetailsProvider,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    designerConfig: DesignerConfig,
)(implicit executionContext: ExecutionContext)
    extends ProcessesWithDetailsProvider {

  val technicalUsers: TechnicalUsers = {
    val technicalUsersPath = "technicalUsers"
    if (designerConfig.rawConfig.resolved.hasPath(technicalUsersPath)) {
      TechnicalUsers(designerConfig.rawConfig.resolved.getStringList(technicalUsersPath).asScala.toSet)
    } else {
      TechnicalUsers(Set(NussknackerInternalUser.instance.username))
    }
  }

  override def getLatestProcessWithDetails(
      rawQueryParams: Map[String, String],
      processId: ProcessIdWithName,
      options: GetScenarioWithDetailsOptions
  )(implicit user: LoggedUser): Future[ScenarioWithDetails] = {
    underlying
      .getLatestProcessWithDetails(rawQueryParams, processId, options)
      .map { processDetails =>
        (for {
          history <- processDetails.history
          versionsByNonTechnicalUsers = history.filter(v => !technicalUsers.userNames.contains(v.user))
          latestVersionByNonTechnicalUsers <-
            if (versionsByNonTechnicalUsers.nonEmpty) {
              Some(versionsByNonTechnicalUsers.maxBy(_.processVersionId.value))
            } else {
              None
            }
        } yield enrichScenarioDetails(
          processDetails,
          latestVersionByNonTechnicalUsers.user,
          latestVersionByNonTechnicalUsers.createDate
        )).getOrElse(processDetails)
      }
  }

  override def getLatestProcessesWithDetails(
      rawQueryParams: Map[String, String],
      query: ScenarioQuery,
      options: GetScenarioWithDetailsOptions
  )(implicit user: LoggedUser): Future[List[ScenarioWithDetails]] = {

    val scenariosWithDetailsF: Future[List[ScenarioWithDetails]] =
      underlying.getLatestProcessesWithDetails(rawQueryParams, query, options)

    val fetchLatestNonTechnicalModification =
      rawQueryParams
        .get(fetchLatestNonTechnicalModificationHeaderName)
        .collect { case "true" => true }
        .getOrElse(false)

    val latestVersionsCreatedByNonTechnicalUsersF = {
      if (fetchLatestNonTechnicalModification) {
        fetchingProcessRepository.fetchLatestProcessVersionsCreatedByNonTechnicalUsers(query, technicalUsers)
      } else {
        Future.successful(Map.empty[ProcessId, (VersionId, Timestamp, ProcessingType)])
      }
    }

    for {
      scenariosWithDetails                     <- scenariosWithDetailsF
      latestVersionsCreatedByNonTechnicalUsers <- latestVersionsCreatedByNonTechnicalUsersF
    } yield {
      scenariosWithDetails.map { scenarioWithDetails =>
        scenarioWithDetails.processId.flatMap(latestVersionsCreatedByNonTechnicalUsers.get) match {
          case Some(latestVersionsCreatedByNonTechnicalUser) =>
            enrichScenarioDetails(
              scenarioWithDetails,
              latestVersionsCreatedByNonTechnicalUser._3,
              latestVersionsCreatedByNonTechnicalUser._2.toInstant
            )
          case None =>
            scenarioWithDetails
        }
      }
    }
  }

  private def enrichScenarioDetails(
      scenarioWithDetails: ScenarioWithDetails,
      modifiedByNonTechnicalUser: String,
      modifiedByNonTechnicalUserAt: Instant
  ): ScenarioWithDetails = {
    scenarioWithDetails.copy(
      additionalDetails = scenarioWithDetails.additionalDetails ++ Map(
        "modifiedByNonTechnicalUser"   -> modifiedByNonTechnicalUser,
        "modifiedByNonTechnicalUserAt" -> modifiedByNonTechnicalUserAt.toString,
      )
    )
  }

}

object EnrichedWithLastNonTechnicalEditionProcessesWithDetailsProvider {

  final case class TechnicalUsers(userNames: Set[String])

  private val fetchLatestNonTechnicalModificationHeaderName: String = "fetchLatestNonTechnicalModification"

}
