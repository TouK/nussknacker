package pl.touk.nussknacker.ui.process

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.{
  PagedVersionsWithDifferences,
  VersionsWithDifferences,
  VersionWithDifference
}
import pl.touk.nussknacker.ui.process.migrate.RemoteEnvironment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object VersionsWithDifferencesService {

  val MinLimit = 1

  val MaxLimit = 25

  val MaxVersionsScannedPerRequest = 100

  val MaxGraphsPerFetch = 25

  val MaxChangedElementsPerVersion = 50

  val MaxSuppliedGraphBytes: Long = 32 * 1024 * 1024

  def isValidPaging(offset: Int, limit: Int): Boolean =
    offset >= 0 && limit >= MinLimit && limit <= MaxLimit

  @JsonCodec final case class VersionWithDifference(
      versionId: VersionId,
      changedElements: List[String],
      differencesUnknown: Boolean,
      comment: Option[String] = None,
      totalChangedElements: Option[Int] = None
  )

  /** The answer for one scenario's whole history, for the cross-environment comparison, which is unpaged. */
  final case class VersionsWithDifferences(
      versions: List[VersionWithDifference],
      // set only by the endpoint proxying to a remote environment, which is the only one with a remote to
      // say anything about
      remoteUnavailable: Option[Boolean] = None
  )

  object VersionsWithDifferences {

    implicit val encoder: Encoder[VersionsWithDifferences] =
      deriveEncoder[VersionsWithDifferences].mapJson(_.deepDropNullValues)

    implicit val decoder: Decoder[VersionsWithDifferences] = deriveDecoder[VersionsWithDifferences]

  }

  final case class PagedVersionsWithDifferences(
      versions: List[VersionWithDifference],
      // counts versions scanned, not versions returned - identical ones are skipped and must not be rescanned
      nextOffset: Option[Int]
  )

  object PagedVersionsWithDifferences {

    implicit val encoder: Encoder[PagedVersionsWithDifferences] =
      deriveEncoder[PagedVersionsWithDifferences].mapJson(_.deepDropNullValues)

    implicit val decoder: Decoder[PagedVersionsWithDifferences] = deriveDecoder[PagedVersionsWithDifferences]

  }

  /**
   * Returns up to `limit` versions that meaningfully differ from `currentGraph`, scanning forward from
   * `offset` past versions that turn out to be identical, up to `MaxVersionsScannedPerRequest`.
   */
  def compute(
      currentGraph: ScenarioGraph,
      allOtherVersionIds: List[VersionId],
      offset: Int,
      limit: Int,
      fetchGraphs: List[VersionId] => Future[Map[VersionId, ScenarioGraph]],
      describeMissingGraph: VersionId => Option[VersionWithDifference] = _ => None
  )(implicit ec: ExecutionContext): Future[PagedVersionsWithDifferences] = {
    val preparedCurrentGraph = ScenarioGraphComparator.PreparedCurrentGraph(currentGraph)

    def describe(versionId: VersionId, graphs: Map[VersionId, ScenarioGraph]): Option[VersionWithDifference] =
      describeVersion(preparedCurrentGraph, versionId, graphs, describeMissingGraph)

    // fetching may overshoot what the page still needs, consuming must not - the overshot versions are
    // what the next request resumes at
    @tailrec
    def takeUntilFull(
        chunk: List[VersionId],
        graphs: Map[VersionId, ScenarioGraph],
        wanted: Int,
        consumed: Int,
        found: List[VersionWithDifference]
    ): (Int, List[VersionWithDifference]) = chunk match {
      case _ if wanted <= 0 => (consumed, found.reverse)
      case Nil              => (consumed, found.reverse)
      case versionId :: rest =>
        describe(versionId, graphs) match {
          case Some(difference) => takeUntilFull(rest, graphs, wanted - 1, consumed + 1, difference :: found)
          case None             => takeUntilFull(rest, graphs, wanted, consumed + 1, found)
        }
    }

    def scan(
        remaining: List[VersionId],
        scanned: Int,
        collected: List[VersionWithDifference]
    ): Future[PagedVersionsWithDifferences] = {
      if (remaining.isEmpty) {
        Future.successful(PagedVersionsWithDifferences(collected, nextOffset = None))
      } else if (collected.size >= limit || scanned >= MaxVersionsScannedPerRequest) {
        Future.successful(PagedVersionsWithDifferences(collected, nextOffset = Some(offset + scanned)))
      } else {
        val chunkSize = math.min(MaxVersionsScannedPerRequest - scanned, MaxGraphsPerFetch)
        val chunk     = remaining.take(chunkSize)
        val wanted    = limit - collected.size
        fetchGraphs(chunk).flatMap { graphs =>
          val (consumed, found) = takeUntilFull(chunk, graphs, wanted, consumed = 0, found = Nil)
          scan(remaining.drop(consumed), scanned + consumed, collected ++ found)
        }
      }
    }

    scan(allOtherVersionIds.drop(offset), scanned = 0, collected = Nil)
  }

  /**
   * Every version that meaningfully differs from `currentGraph`, with no paging and no scan budget. For
   * the cross-environment comparison, where asking costs a whole scenario graph in the request body and
   * the answer is only a summary per version - paging it would re-send the graph rather than save anything.
   */
  def computeAll(
      currentGraph: ScenarioGraph,
      allVersionIds: List[VersionId],
      fetchGraphs: List[VersionId] => Future[Map[VersionId, ScenarioGraph]],
      describeMissingGraph: VersionId => Option[VersionWithDifference] = _ => None
  )(implicit ec: ExecutionContext): Future[VersionsWithDifferences] = {
    val preparedCurrentGraph = ScenarioGraphComparator.PreparedCurrentGraph(currentGraph)

    def scan(remaining: List[VersionId], collected: List[VersionWithDifference]): Future[List[VersionWithDifference]] =
      if (remaining.isEmpty) {
        Future.successful(collected)
      } else {
        val chunk = remaining.take(MaxGraphsPerFetch)
        fetchGraphs(chunk).flatMap { graphs =>
          val found = chunk.flatMap(describeVersion(preparedCurrentGraph, _, graphs, describeMissingGraph))
          scan(remaining.drop(chunk.size), collected ++ found)
        }
      }

    scan(allVersionIds, Nil).map(VersionsWithDifferences(_))
  }

  private def describeVersion(
      preparedCurrentGraph: ScenarioGraphComparator.PreparedCurrentGraph,
      versionId: VersionId,
      graphs: Map[VersionId, ScenarioGraph],
      describeMissingGraph: VersionId => Option[VersionWithDifference]
  ): Option[VersionWithDifference] =
    graphs.get(versionId) match {
      case Some(otherGraph) =>
        val changed = ScenarioGraphComparator.describeMeaningfulDiffs(preparedCurrentGraph.compareWith(otherGraph))
        Option.when(changed.nonEmpty)(
          VersionWithDifference(
            versionId,
            changed.take(MaxChangedElementsPerVersion),
            differencesUnknown = false,
            totalChangedElements = Option.when(changed.sizeIs > MaxChangedElementsPerVersion)(changed.size)
          )
        )
      case None =>
        describeMissingGraph(versionId)
    }

}

class VersionsWithDifferencesService(processService: ProcessService) {

  def computeForLocalVersions(
      processIdWithName: ProcessIdWithName,
      currentVersionId: VersionId,
      offset: Int,
      limit: Int
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[PagedVersionsWithDifferences] = {
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
        offset,
        limit,
        fetchGraphs = page => processService.getScenarioGraphsForVersionIds(processIdWithName, page)
      )
    } yield result
  }

  /**
   * Which of our versions of this scenario differ from a graph supplied by another environment - the peer
   * half of `computeForRemoteVersions`.
   */
  def computeAgainstSuppliedGraph(
      processIdWithName: ProcessIdWithName,
      suppliedGraph: ScenarioGraph
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    for {
      details <- processService.getLatestProcessWithDetails(
        processIdWithName,
        GetScenarioWithDetailsOptions.detailsOnly
      )
      allVersionIds = details.history.getOrElse(Nil).map(_.processVersionId)
      result <- VersionsWithDifferencesService.computeAll(
        suppliedGraph,
        allVersionIds,
        fetchGraphs = page => processService.getScenarioGraphsForVersionIds(processIdWithName, page),
        describeMissingGraph = versionId => Some(VersionWithDifference(versionId, Nil, differencesUnknown = true))
      )
    } yield result
  }

  def computeForRemoteVersions(
      remoteEnvironment: RemoteEnvironment,
      processIdWithName: ProcessIdWithName,
      currentLocalVersionId: VersionId
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[VersionsWithDifferences] = {
    for {
      localDetails <- processService.getProcessWithDetails(
        processIdWithName,
        currentLocalVersionId,
        GetScenarioWithDetailsOptions.withScenarioGraph
      )
      remoteResultFuture = remoteEnvironment.versionsWithDifferences(
        processIdWithName.name,
        localDetails.scenarioGraphUnsafe
      )
      commentsFuture = remoteEnvironment.activities(processIdWithName.name).map(versionComments)
      remoteResult <- remoteResultFuture
      comments     <- commentsFuture
    } yield remoteResult
      .map(result =>
        result.copy(versions = result.versions.map(v => v.copy(comment = comments.get(v.versionId.value))))
      )
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
