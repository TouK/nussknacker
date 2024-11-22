package pl.touk.nussknacker.ui.process.fragment

import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait FragmentRepository {

  // FIXME: async version should be used instead
  final def fetchLatestFragmentsSync(processingType: ProcessingType)(
      implicit user: LoggedUser
  ): List[CanonicalProcess] =
    Await.result(fetchLatestFragments(processingType), 10 seconds)

  def fetchLatestFragments(processingType: ProcessingType)(implicit user: LoggedUser): Future[List[CanonicalProcess]]

  // FIXME: async version should be used instead
  final def fetchLatestFragmentSync(fragmentName: ProcessName)(implicit user: LoggedUser): Option[CanonicalProcess] =
    Await.result(fetchLatestFragment(fragmentName), 10 seconds)

  def fetchLatestFragment(fragmentName: ProcessName)(implicit user: LoggedUser): Future[Option[CanonicalProcess]]

}

class DefaultFragmentRepository(processRepository: FetchingProcessRepository[Future])(implicit ec: ExecutionContext)
    extends FragmentRepository {

  override def fetchLatestFragments(
      processingType: ProcessingType
  )(implicit user: LoggedUser): Future[List[CanonicalProcess]] = {
    processRepository
      .fetchLatestProcessesDetails[CanonicalProcess](
        ScenarioQuery(isFragment = Some(true), isArchived = Some(false), processingTypes = Some(List(processingType)))
      )
      .map(_.map(_.json))
  }

  override def fetchLatestFragment(
      fragmentName: ProcessName
  )(implicit user: LoggedUser): Future[Option[CanonicalProcess]] = {
    processRepository
      .fetchProcessId(fragmentName)
      .flatMap(_.map(processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess]).sequence.map(_.flatten))
      .map(_.map(_.json))
  }

}
