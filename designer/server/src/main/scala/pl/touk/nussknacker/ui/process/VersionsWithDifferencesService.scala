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

// Computes one page of "versions with meaningful differences from the current graph", shared by the local
// (ProcessesResources) and remote (RemoteEnvironmentResources) versions-with-differences routes, so the actual
// diffing/pagination/filtering logic doesn't live in - and isn't duplicated across - the REST resource classes.
object VersionsWithDifferencesService {

  val PageSize: Int = 10

  @JsonCodec final case class VersionWithDifference(versionId: VersionId, changedElements: List[String])

  @JsonCodec final case class VersionsWithDifferences(
      versions: List[VersionWithDifference],
      hasMore: Boolean,
      pageSize: Int
  )

  def computeForLocalVersions(
      processService: ProcessService,
      processIdWithName: ProcessIdWithName,
      currentVersionId: VersionId,
      offset: Int
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
        offset,
        fetchGraphs = page => processService.getScenarioGraphsForVersionIds(processIdWithName, page)
      )
    } yield result
  }

  def computeForRemoteVersions(
      processService: ProcessService,
      remoteEnvironment: RemoteEnvironment,
      processIdWithName: ProcessIdWithName,
      currentLocalVersionId: VersionId,
      offset: Int
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
        offset,
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

  /**
    * @param fetchGraphs fetches the scenario graphs for a page of version ids - a local DB lookup for
    *                     ProcessesResources, or a bulk HTTP call to a remote environment for RemoteEnvironmentResources.
    * @param describeMissingGraph called for a version whose graph `fetchGraphs` didn't return (e.g. a remote
    *                              environment too old to support the bulk-fetch endpoint). Defaults to dropping the
    *                              version; RemoteEnvironmentResources overrides it to conservatively mark it as
    *                              different instead, since it can't tell whether it actually differs.
    */
  def compute(
      currentGraph: ScenarioGraph,
      allOtherVersionIds: List[VersionId],
      offset: Int,
      fetchGraphs: List[VersionId] => Future[Map[VersionId, ScenarioGraph]],
      describeMissingGraph: VersionId => Option[VersionWithDifference] = _ => None
  )(implicit ec: ExecutionContext): Future[VersionsWithDifferences] = {
    val (page, hasMore) = paginate(allOtherVersionIds, offset)
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
        hasMore = hasMore,
        pageSize = PageSize
      )
    }
  }

  private def paginate(ids: List[VersionId], offset: Int): (List[VersionId], Boolean) = {
    val page = ids.slice(offset, offset + PageSize)
    (page, offset + PageSize < ids.size)
  }

}
