package pl.touk.nussknacker.test.mock

import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

class StubFragmentRepository(val fragmentsByProcessingType: Map[ProcessingType, List[CanonicalProcess]])
    extends FragmentRepository {

  override def fetchLatestFragments(processingType: ProcessingType)(
      implicit user: LoggedUser
  ): Future[List[CanonicalProcess]] =
    Future.successful(fragmentsByProcessingType.getOrElse(processingType, List.empty))

  override def fetchLatestFragment(fragmentName: ProcessName)(
      implicit user: LoggedUser
  ): Future[Option[CanonicalProcess]] =
    Future.successful(fragmentsByProcessingType.values.flatten.find(_.name == fragmentName))

}
