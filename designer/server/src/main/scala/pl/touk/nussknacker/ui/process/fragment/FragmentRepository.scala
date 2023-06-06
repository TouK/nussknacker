package pl.touk.nussknacker.ui.process.fragment

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// FIXME: why everywhere pass Map.empty as a version - looks like it can be cleaned
// FIXME: Map[id->process] instead of Sets to avoid unnecessary hashcode computation overhead and easier usage
trait FragmentRepository {

  def loadFragments(versions: Map[String, VersionId]): Set[FragmentDetails]

  def loadFragments(versions: Map[String, VersionId], category: String): Set[FragmentDetails]

  // FIXME: get by id in DB
  def get(id: String): Option[FragmentDetails] = loadFragments().find(_.canonical.metaData.id == id)

  // FIXME: load only ids from DB
  def loadFragmentIds(): List[String] = loadFragments(Map.empty).map(_.canonical.metaData.id).toList

  def loadFragments(): Set[FragmentDetails] = loadFragments(Map.empty)

}

case class FragmentDetails(canonical: CanonicalProcess, category: String)

class DbFragmentRepository(db: DbConfig, ec: ExecutionContext) extends FragmentRepository {

  private val dbioRunner = DBIOActionRunner(db)

  //TODO: make it return Future?
  override def loadFragments(versions: Map[String, VersionId]): Set[FragmentDetails] = {
    Await.result(listFragments(versions, None), 10 seconds)
  }

  override def loadFragments(versions: Map[String, VersionId], category: String): Set[FragmentDetails] = {
    Await.result(listFragments(versions, Some(category)), 10 seconds)
  }

  import db.profile.api._

  val espTables = new EspTables {
    override implicit val profile: JdbcProfile = db.profile
  }

  import espTables._

  implicit val iec = ec

  //Fetches fragment in given version if specified, fetches latest version otherwise
  def listFragments(versions: Map[String, VersionId], category: Option[String]): Future[Set[FragmentDetails]] = {
    val versionFragments = Future.sequence {
      versions.map { case (fragmentId, fragmentVersion) =>
        fetchFragment(ProcessName(fragmentId), fragmentVersion, category)
      }
    }
    val fetchedFragments = for {
      fragments <- versionFragments
      latestFragments <- listLatestFragments(category)
    } yield latestFragments.groupBy(_.canonical.metaData.id).mapValuesNow(_.head) ++ fragments.groupBy(_.canonical.metaData.id).mapValuesNow(_.head)

    fetchedFragments.map(_.values.toSet)
  }

  private def listLatestFragments(category: Option[String]): Future[Set[FragmentDetails]] = {
    val action = for {
      latestProcesses <- processVersionsTableWithUnit.groupBy(_.processId).map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTableWithScenarioJson).on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }.join(fragmentsQuery(category))
        .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result
    } yield latestProcesses.map { case ((_, processVersion), process) =>
      createFragmentDetails(process, processVersion)
    }
    dbioRunner.run(action).map(_.flatten.toSet)
    dbioRunner.run(action).map(_.flatten.toSet)
  }

  private def fetchFragment(fragmentName: ProcessName, version: VersionId, category: Option[String]): Future[FragmentDetails] = {
    val action = for {
      fragmentVersion <- processVersionsTableWithScenarioJson.filter(p => p.id === version)
        .join(fragmentsQueryByName(fragmentName, category))
        .on { case (latestVersion, process) => latestVersion.processId === process.id }
        .result.headOption
    } yield fragmentVersion.flatMap { case (processVersion, process) =>
      createFragmentDetails(process, processVersion)
    }

    dbioRunner.run(action).flatMap {
      case Some(subproc) => Future.successful(subproc)
      case None => Future.failed(new Exception(s"Fragment $fragmentName, version: $version not found"))
    }
  }

  private def createFragmentDetails(process: ProcessEntityData, processVersion: ProcessVersionEntityData): Option[FragmentDetails] =
    processVersion.json.map { canonical => FragmentDetails(canonical, process.processCategory) }

  private def fragmentsQuery(category: Option[String]) = {
    val query = processesTable.filter(_.isFragment).filter(!_.isArchived)

    category
      .map { cat => query.filter(p => p.processCategory === cat) }
      .getOrElse(query)
  }

  private def fragmentsQueryByName(fragmentName: ProcessName, category: Option[String]) =
    fragmentsQuery(category).filter(_.name === fragmentName)
}


