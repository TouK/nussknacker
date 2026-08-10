package pl.touk.nussknacker.ui.process

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentRelatedActivity,
  DeploymentResult,
  ScenarioActivity,
  ScenarioComment,
  SchedulingRelatedActivity
}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.{VersionsWithDifferences, VersionWithDifference}
import pl.touk.nussknacker.ui.process.migrate.RemoteEnvironment
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object VersionsWithDifferencesService {

  val DefaultVersionsCompared = 50

  val MinVersionsCompared = 1

  val MaxVersionsCompared = 500

  val MaxGraphsPerFetch = 25

  val MaxChangedElementsPerVersion = 50

  def isValidLimit(limit: Int): Boolean = limit >= MinVersionsCompared && limit <= MaxVersionsCompared

  /**
   * The latest comment describing each version. Scheduling-related activities are left out entirely - a
   * comment given when running a scenario describes that run, not the version - and so are activities for
   * deployments that failed, matching what the activities endpoint shows.
   */
  def versionComments(activities: List[ScenarioActivity]): Map[Long, String] =
    activities
      .sortBy(_.date)
      .flatMap { activity =>
        for {
          _         <- Option.when(describesVersion(activity))(())
          versionId <- activity.scenarioVersionId
          content   <- commentContent(activity)
        } yield versionId.value -> content
      }
      .toMap

  private def describesVersion(activity: ScenarioActivity): Boolean = activity match {
    case _: SchedulingRelatedActivity => false
    case deployment: DeploymentRelatedActivity =>
      deployment.result match {
        case _: DeploymentResult.Success => true
        case _: DeploymentResult.Failure => false
      }
    case _ => true
  }

  private def commentContent(activity: ScenarioActivity): Option[String] = {
    val comment = activity match {
      case a: ScenarioActivity.ScenarioDeployed   => Some(a.comment)
      case a: ScenarioActivity.ScenarioRedeployed => Some(a.comment)
      case a: ScenarioActivity.ScenarioPaused     => Some(a.comment)
      case a: ScenarioActivity.ScenarioCanceled   => Some(a.comment)
      case a: ScenarioActivity.ScenarioModified   => Some(a.comment)
      case a: ScenarioActivity.CommentAdded       => Some(a.comment)
      case _                                      => None
    }
    comment.collect { case ScenarioComment.WithContent(content, _, _) => content.content }.filter(_.nonEmpty)
  }

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
      // every version's comment, not only the compared ones - always the comments of the environment
      // that computed this answer, so a proxied one carries the remote's
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

class VersionsWithDifferencesService(
    processService: ProcessService,
    scenarioActivityRepository: ScenarioActivityRepository,
    dbioActionRunner: DBIOActionRunner
) extends LazyLogging {

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
      commentsFuture = versionComments(processIdWithName)
      result <- VersionsWithDifferencesService.compute(
        currentDetails.scenarioGraphUnsafe,
        allOtherVersionIds,
        limit,
        fetchGraphs = chunk => processService.getScenarioGraphsForVersionIds(processIdWithName, chunk)
      )
      comments <- commentsFuture
    } yield result.copy(versionComments = comments)
  }

  // covers every version, including ones too old to have been compared - they come from the activity
  // list, which costs no scenario graph
  private def versionComments(
      processIdWithName: ProcessIdWithName
  )(implicit ec: ExecutionContext): Future[Option[Map[Long, String]]] =
    dbioActionRunner
      .run(scenarioActivityRepository.findActivities(processIdWithName.id))
      .map(activities => VersionsWithDifferencesService.versionComments(activities.toList))
      .map(comments => Option.when(comments.nonEmpty)(comments))
      // the comments only label the versions - failing to read them must not take the comparison down
      .recover { case NonFatal(ex) =>
        logger.warn(s"Failed to read version comments for scenario ${processIdWithName.name.value}", ex)
        None
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
      allVersionIds  = details.history.getOrElse(Nil).map(_.processVersionId)
      commentsFuture = versionComments(processIdWithName)
      result <- VersionsWithDifferencesService.compute(
        suppliedGraph,
        allVersionIds,
        limit,
        fetchGraphs = chunk => processService.getScenarioGraphsForVersionIds(processIdWithName, chunk)
      )
      comments <- commentsFuture
    } yield result.copy(versionComments = comments)
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
      // the remote answers with its own versions' comments, so nothing is merged in here
      remoteResult <- remoteEnvironment.versionsWithDifferences(
        processIdWithName.name,
        localDetails.scenarioGraphUnsafe,
        limit
      )
    } yield remoteResult.getOrElse(VersionsWithDifferences(Nil, remoteUnavailable = Some(true)))
  }

}
