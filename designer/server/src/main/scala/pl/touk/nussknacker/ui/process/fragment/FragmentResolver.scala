package pl.touk.nussknacker.ui.process.fragment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.security.api.LoggedUser

class FragmentResolver(fragmentRepository: FragmentRepository) {

  def resolveFragments(
      process: CanonicalProcess,
      processingType: ProcessingType
  )(implicit user: LoggedUser): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val fragments =
      fragmentRepository
        .fetchLatestFragmentsSync(processingType)
        .map(s => s.name -> s)
        .toMap
    pl.touk.nussknacker.engine.compile.FragmentResolver(fragments.get _).resolve(process)
  }

}
