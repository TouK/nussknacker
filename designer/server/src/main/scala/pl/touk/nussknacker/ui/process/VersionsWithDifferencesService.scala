package pl.touk.nussknacker.ui.process

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.{VersionsWithDifferences, VersionWithDifference}
import pl.touk.nussknacker.ui.process.migrate.RemoteEnvironment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator

import scala.concurrent.{ExecutionContext, Future}

object VersionsWithDifferencesService {

  // How many of the most recent versions are compared when the caller does not say. Comparing a version
  // means loading and diffing its stored graph, so the whole history is not something to walk by default.
  val DefaultVersionsCompared = 50

  val MinVersionsCompared = 1

  val MaxVersionsCompared = 500

  val MaxGraphsPerFetch = 25

  val MaxChangedElementsPerVersion = 50

  // Generous next to any real scenario graph, but small enough that parsing one is not itself an attack.
  val MaxSuppliedGraphBytes: Long = 8 * 1024 * 1024

  // Nodes plus edges. What bounds the comparison work per version - the byte limit above still admits a
  // graph with far more elements than any real scenario, and each one costs a diff entry per version.
  val MaxSuppliedGraphElements = 20000

  def isValidLimit(limit: Int): Boolean = limit >= MinVersionsCompared && limit <= MaxVersionsCompared

  @JsonCodec final case class VersionWithDifference(
      versionId: VersionId,
      changedElements: List[String],
      differencesUnknown: Boolean,
      totalChangedElements: Option[Int] = None
  )

  final case class VersionsWithDifferences(
      versions: List[VersionWithDifference],
      // The oldest version this answer covers. Versions older than it were not compared, so nothing is
      // claimed about them either way. Absent when the whole history was compared.
      oldestComparedVersionId: Option[VersionId] = None,
      // Every version's comment, including versions that were not compared or do not differ - they come
      // from the activity list, which costs no graph. Set only by the endpoint proxying to a remote
      // environment; for local versions the client already holds the activities.
      versionComments: Map[Long, String] = Map.empty,
      // set only by the endpoint proxying to a remote environment, which is the only one with a remote to
      // say anything about
      remoteUnavailable: Option[Boolean] = None
  )

  object VersionsWithDifferences {

    implicit val encoder: Encoder[VersionsWithDifferences] =
      deriveEncoder[VersionsWithDifferences].mapJson(_.deepDropNullValues)

    // Spelled out rather than derived: a derived decoder does not apply case-class defaults, so an
    // answer that omits `versionComments` would fail to decode instead of meaning "none".
    implicit val decoder: Decoder[VersionsWithDifferences] = Decoder.instance { cursor =>
      for {
        versions          <- cursor.get[List[VersionWithDifference]]("versions")
        oldestCompared    <- cursor.get[Option[VersionId]]("oldestComparedVersionId")
        comments          <- cursor.getOrElse[Map[Long, String]]("versionComments")(Map.empty)
        remoteUnavailable <- cursor.get[Option[Boolean]]("remoteUnavailable")
      } yield VersionsWithDifferences(versions, oldestCompared, comments, remoteUnavailable)
    }

  }

  /**
   * Which of the `limit` most recent versions meaningfully differ from `currentGraph`, fetching their
   * graphs a chunk at a time. `allVersionIds` is expected newest-first.
   */
  def compute(
      currentGraph: ScenarioGraph,
      allVersionIds: List[VersionId],
      limit: Int,
      fetchGraphs: List[VersionId] => Future[Map[VersionId, ScenarioGraph]]
  )(implicit ec: ExecutionContext): Future[VersionsWithDifferences] = {
    val preparedCurrentGraph = ScenarioGraphComparator.PreparedCurrentGraph(currentGraph)
    val compared             = allVersionIds.take(limit)
    // `allVersionIds` has to be newest-first: `take` is what makes this the most recent versions, and the
    // boundary reported below is only meaningful because of it.
    val oldestCompared =
      Option.when(compared.nonEmpty && compared.sizeCompare(allVersionIds) < 0)(compared.minBy(_.value))

    def scan(remaining: List[VersionId], collected: List[VersionWithDifference]): Future[List[VersionWithDifference]] =
      if (remaining.isEmpty) {
        Future.successful(collected)
      } else {
        val chunk = remaining.take(MaxGraphsPerFetch)
        fetchGraphs(chunk).flatMap { graphs =>
          val found = chunk.flatMap(describeVersion(preparedCurrentGraph, _, graphs))
          scan(remaining.drop(chunk.size), collected ++ found)
        }
      }

    scan(compared, Nil).map(VersionsWithDifferences(_, oldestCompared))
  }

  private def describeVersion(
      preparedCurrentGraph: ScenarioGraphComparator.PreparedCurrentGraph,
      versionId: VersionId,
      graphs: Map[VersionId, ScenarioGraph]
  ): Option[VersionWithDifference] =
    graphs.get(versionId) match {
      case Some(otherGraph) =>
        val (changed, totalChanged) = ScenarioGraphComparator.describeMeaningfulDiffs(
          preparedCurrentGraph.compareWith(otherGraph),
          MaxChangedElementsPerVersion
        )
        Option.when(totalChanged > 0)(
          VersionWithDifference(
            versionId,
            changed,
            differencesUnknown = false,
            totalChangedElements = Option.when(totalChanged > MaxChangedElementsPerVersion)(totalChanged)
          )
        )
      // a version we hold but cannot decode - reported rather than dropped, so it stays selectable and says
      // why it cannot describe itself
      case None =>
        Some(VersionWithDifference(versionId, Nil, differencesUnknown = true))
    }

}

class VersionsWithDifferencesService(processService: ProcessService) {

  def computeForLocalVersions(
      processIdWithName: ProcessIdWithName,
      currentVersionId: VersionId,
      limit: Int
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
        limit,
        fetchGraphs = chunk => processService.getScenarioGraphsForVersionIds(processIdWithName, chunk)
      )
    } yield result
  }

  /**
   * Which of our versions of this scenario differ from a graph supplied by another environment - the peer
   * half of `computeForRemoteVersions`.
   */
  def computeAgainstSuppliedGraph(
      processIdWithName: ProcessIdWithName,
      suppliedGraph: ScenarioGraph,
      limit: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    for {
      details <- processService.getLatestProcessWithDetails(
        processIdWithName,
        GetScenarioWithDetailsOptions.detailsOnly
      )
      allVersionIds = details.history.getOrElse(Nil).map(_.processVersionId)
      result <- VersionsWithDifferencesService.compute(
        suppliedGraph,
        allVersionIds,
        limit,
        fetchGraphs = chunk => processService.getScenarioGraphsForVersionIds(processIdWithName, chunk)
      )
    } yield result
  }

  def computeForRemoteVersions(
      remoteEnvironment: RemoteEnvironment,
      processIdWithName: ProcessIdWithName,
      currentLocalVersionId: VersionId,
      limit: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    for {
      localDetails <- processService.getProcessWithDetails(
        processIdWithName,
        currentLocalVersionId,
        GetScenarioWithDetailsOptions.withScenarioGraph
      )
      remoteResultFuture = remoteEnvironment.versionsWithDifferences(
        processIdWithName.name,
        localDetails.scenarioGraphUnsafe,
        limit
      )
      commentsFuture = remoteEnvironment.activities(processIdWithName.name).map(versionComments)
      remoteResult <- remoteResultFuture
      comments     <- commentsFuture
    } yield remoteResult
      .map(_.copy(versionComments = comments))
      .getOrElse(VersionsWithDifferences(Nil, remoteUnavailable = Some(true)))
  }

  // a comment belongs to the version whose id its activity carries; the newest one describes it
  private def versionComments(activities: List[Dtos.ScenarioActivity]): Map[Long, String] =
    activities
      .sortBy(_.date)
      .flatMap { activity =>
        for {
          versionId <- activity.scenarioVersionId
          comment   <- activity.comment
          value <- comment.content match {
            case Dtos.ScenarioActivityCommentContent.Available(value) if value.nonEmpty => Some(value)
            case _                                                                      => None
          }
        } yield versionId -> value
      }
      .toMap

}
