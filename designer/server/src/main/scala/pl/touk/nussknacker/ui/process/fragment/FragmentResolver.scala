package pl.touk.nussknacker.ui.process.fragment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class FragmentResolver(fragmentRepository: FragmentRepository) {

  def fetchFragmentsSync(
      processingType: ProcessingType
  )(implicit user: LoggedUser): List[CanonicalProcess] =
    fragmentRepository.fetchLatestFragmentsSync(processingType)

  def resolveFragments(
      process: CanonicalProcess,
      fragments: List[CanonicalProcess]
  ): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val fragmentsMap = fragments.map(s => s.name -> s).toMap
    compile.FragmentResolver(fragmentsMap.get _).resolve(process)
  }

  def resolveFragmentsAsync(
      process: CanonicalProcess,
      processingType: ProcessingType
  )(
      implicit user: LoggedUser,
      executionContext: ExecutionContext
  ): Future[ValidatedNel[ProcessCompilationError, CanonicalProcess]] =
    fragmentRepository
      .fetchLatestFragments(processingType)
      .map { fragments =>
        val groupedFragments = fragments.map(s => s.name -> s).toMap
        compile.FragmentResolver(groupedFragments.get _).resolve(process)
      }

}
