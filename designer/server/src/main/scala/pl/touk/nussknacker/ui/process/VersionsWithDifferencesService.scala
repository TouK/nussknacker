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

  val DefaultVersionsCompared = 50

  val MinVersionsCompared = 1

  val MaxVersionsCompared = 500

  val MaxGraphsPerFetch = 25

  val MaxChangedElementsPerVersion = 50

  val MaxSuppliedGraphBytes: Long = 8 * 1024 * 1024

  // nodes plus edges - the byte limit above still admits far more elements than any real scenario, and
  // each one costs a diff entry per version compared
  val MaxSuppliedGraphElements = 20000

  def isValidLimit(limit: Int): Boolean = limit >= MinVersionsCompared && limit <= MaxVersionsCompared

  def suppliedGraphTooLargeError(graph: ScenarioGraph): Option[String] =
    Option.when(graph.nodes.size + graph.edges.size > MaxSuppliedGraphElements)(
      s"scenario graph has more than $MaxSuppliedGraphElements nodes and edges"
    )

  @JsonCodec final case class VersionWithDifference(
      versionId: VersionId,
      changedElements: List[String],
      differencesUnknown: Boolean,
      totalChangedElements: Option[Int] = None
  )

  final case class VersionsWithDifferences(
      versions: List[VersionWithDifference],
      // versions older than this one were not compared; absent when the whole history was
      oldestComparedVersionId: Option[VersionId] = None,
      // every version's comment, not only the compared ones; set only by the endpoint proxying to a
      // remote environment, since for local versions the client already holds the activities
      versionComments: Option[Map[Long, String]] = None,
      remoteUnavailable: Option[Boolean] = None
  )

  object VersionsWithDifferences {

    // the encoder is not derived alone, because absent has to be distinguishable from empty on the wire
    implicit val encoder: Encoder[VersionsWithDifferences] =
      deriveEncoder[VersionsWithDifferences].mapJson(_.deepDropNullValues)

    implicit val decoder: Decoder[VersionsWithDifferences] = deriveDecoder[VersionsWithDifferences]

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
      // a version we hold but cannot decode - reported rather than dropped, so it stays selectable
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
   * half of `computeForRemoteVersions`. Left is a graph too large to compare, to be reported as a 400.
   */
  def computeAgainstSuppliedGraph(
      processIdWithName: ProcessIdWithName,
      suppliedGraph: ScenarioGraph,
      limit: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[String, VersionsWithDifferences]] = {
    VersionsWithDifferencesService.suppliedGraphTooLargeError(suppliedGraph) match {
      case Some(error) => Future.successful(Left(error))
      case None =>
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
        } yield Right(result)
    }
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
      .map(_.copy(versionComments = Option.when(comments.nonEmpty)(comments)))
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
