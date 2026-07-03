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

  @JsonCodec final case class VersionWithDifference(versionId: VersionId, changedElements: List[String])

  @JsonCodec final case class VersionsWithDifferences(
      versions: List[VersionWithDifference],
      hasMore: Boolean
  )

  def computeForLocalVersions(
      processService: ProcessService,
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
      result <- compute(
        currentDetails.scenarioGraphUnsafe,
        allOtherVersionIds,
        pageNumber,
        pageSize,
        fetchGraphs = page => processService.getScenarioGraphsForVersionIds(processIdWithName, page)
      )
    } yield result
  }

  def computeForRemoteVersions(
      processService: ProcessService,
      remoteEnvironment: RemoteEnvironment,
      processIdWithName: ProcessIdWithName,
      currentLocalVersionId: VersionId,
      pageNumber: Int,
      pageSize: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    for {
      localDetails <- processService.getProcessWithDetails(
        processIdWithName,
        currentLocalVersionId,
        GetScenarioWithDetailsOptions.withScenarioGraph
      )
      allRemoteVersions <- remoteEnvironment.processVersions(processIdWithName.name)
      result <- compute(
        localDetails.scenarioGraphUnsafe,
        allRemoteVersions.map(_.processVersionId),
        pageNumber,
        pageSize,
        // A single bulk round trip for the whole page, instead of one remote HTTP call per version.
        fetchGraphs = page => remoteEnvironment.scenarioGraphsForVersions(processIdWithName.name, page),
        // The remote environment didn't return a graph for this version (e.g. it's running a
        // Nussknacker version older than this bulk-fetch endpoint). We can't tell whether it
        // actually differs, so we conservatively mark it as different rather than silently
        // hiding a version that might have real, unreviewed changes.
        describeMissingGraph = versionId =>
          Some(VersionWithDifference(versionId, List("Unable to determine differences with the remote environment")))
      )
    } yield result
  }

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
              Option.when(descriptions.nonEmpty)(VersionWithDifference(versionId, descriptions))
            case None =>
              describeMissingGraph(versionId)
          }
        },
        hasMore = hasMore
      )
    }
  }

  private def paginate(ids: List[VersionId], pageNumber: Int, pageSize: Int): (List[VersionId], Boolean) = {
    val offset = pageNumber * pageSize
    val page   = ids.slice(offset, offset + pageSize)
    (page, offset + pageSize < ids.size)
  }

}
