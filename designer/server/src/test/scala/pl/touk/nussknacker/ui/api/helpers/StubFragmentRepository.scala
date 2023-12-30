package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.ui.process.fragment.{FragmentDetails, FragmentRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

class StubFragmentRepository(val fragmentsByProcessingType: Map[ProcessingType, List[FragmentDetails]])
    extends FragmentRepository {

  override def fetchLatestFragments(processingType: ProcessingType)(
      implicit user: LoggedUser
  ): Future[List[FragmentDetails]] =
    Future.successful(fragmentsByProcessingType.getOrElse(processingType, List.empty))

  override def fetchLatestFragment(processName: ProcessName)(
      implicit user: LoggedUser
  ): Future[Option[FragmentDetails]] =
    Future.successful(fragmentsByProcessingType.values.flatten.find(_.canonical.name == processName))

}
