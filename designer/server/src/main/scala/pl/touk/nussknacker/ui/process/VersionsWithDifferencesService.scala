package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.{VersionsWithDifferences, VersionWithDifference}
import pl.touk.nussknacker.ui.process.migrate.RemoteEnvironment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator

import scala.concurrent.{ExecutionContext, Future}

object VersionsWithDifferencesService {

  val MinPageSize = 1
  val MaxPageSize = 100

  def isValidPaging(pageNumber: Int, pageSize: Int): Boolean =
    pageNumber >= 0 && pageSize >= MinPageSize && pageSize <= MaxPageSize

  @JsonCodec final case class VersionWithDifference(
      versionId: VersionId,
      changedElements: List[String],
      differencesUnknown: Boolean
  )

  @JsonCodec final case class VersionsWithDifferences(
      versions: List[VersionWithDifference],
      hasMore: Boolean,
      remoteUnavailable: Boolean = false
  )

  def compute(
      currentGraph: ScenarioGraph,
      allOtherVersionIds: List[VersionId],
      pageNumber: Int,
      pageSize: Int,
      fetchGraphs: List[VersionId] => Future[Map[VersionId, ScenarioGraph]],
      describeMissingGraph: VersionId => Option[VersionWithDifference] = _ => None
  )(implicit ec: ExecutionContext): Future[VersionsWithDifferences] = {
    val (page, hasMore) = paginate(allOtherVersionIds, pageNumber, pageSize)
    fetchGraphs(page).map { graphs =>
      VersionsWithDifferences(
        versions = page.flatMap { versionId =>
          graphs.get(versionId) match {
            case Some(otherGraph) =>
              val descriptions = ScenarioGraphComparator.describeMeaningfulDiffs(
                ScenarioGraphComparator.compare(currentGraph, otherGraph)
              )
              Option.when(descriptions.nonEmpty)(
                VersionWithDifference(versionId, descriptions, differencesUnknown = false)
              )
            case None =>
              describeMissingGraph(versionId)
          }
        },
        hasMore = hasMore
      )
    }
  }

  private def paginate(ids: List[VersionId], pageNumber: Int, pageSize: Int): (List[VersionId], Boolean) = {
    val offset = pageNumber.toLong * pageSize
    if (offset >= ids.size) {
      (Nil, false)
    } else {
      val offsetInt = offset.toInt
      (ids.slice(offsetInt, offsetInt + pageSize), offsetInt + pageSize < ids.size)
    }
  }

}

class VersionsWithDifferencesService(processService: ProcessService) {

  def computeForLocalVersions(
      processIdWithName: ProcessIdWithName,
      currentVersionId: VersionId,
      pageNumber: Int,
      pageSize: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    for {
      currentDetails <- processService.getProcessWithDetails(
        processIdWithName,
        currentVersionId,
        GetScenarioWithDetailsOptions.withScenarioGraph
      )
      allOtherVersionIds = currentDetails.history
        .getOrElse(Nil)
        .map(_.processVersionId)
        .filterNot(_ == currentVersionId)
      result <- VersionsWithDifferencesService.compute(
        currentDetails.scenarioGraphUnsafe,
        allOtherVersionIds,
        pageNumber,
        pageSize,
        fetchGraphs = page => processService.getScenarioGraphsForVersionIds(processIdWithName, page)
      )
    } yield result
  }

  def computeForRemoteVersions(
      remoteEnvironment: RemoteEnvironment,
      processIdWithName: ProcessIdWithName,
      currentLocalVersionId: VersionId,
      pageNumber: Int,
      pageSize: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    val localDetailsFuture = processService.getProcessWithDetails(
      processIdWithName,
      currentLocalVersionId,
      GetScenarioWithDetailsOptions.withScenarioGraph
    )
    val allRemoteVersionsFuture = remoteEnvironment.processVersions(processIdWithName.name)
    for {
      localDetails      <- localDetailsFuture
      allRemoteVersions <- allRemoteVersionsFuture
      result <-
        if (allRemoteVersions.remoteUnavailable) {
          Future.successful(VersionsWithDifferences(Nil, hasMore = false, remoteUnavailable = true))
        } else
          VersionsWithDifferencesService.compute(
            localDetails.scenarioGraphUnsafe,
            allRemoteVersions.versions.map(_.processVersionId),
            pageNumber,
            pageSize,
            fetchGraphs = page => remoteEnvironment.scenarioGraphsForVersions(processIdWithName.name, page),
            describeMissingGraph = versionId => Some(VersionWithDifference(versionId, Nil, differencesUnknown = true))
          )
    } yield result
  }

}
