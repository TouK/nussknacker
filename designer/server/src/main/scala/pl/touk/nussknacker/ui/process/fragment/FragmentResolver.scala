package pl.touk.nussknacker.ui.process.fragment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.security.api.LoggedUser

class FragmentResolver(fragmentRepository: FragmentRepository) {

  def resolveFragments(
      process: CanonicalProcess,
      processingType: ProcessingType
  )(implicit user: LoggedUser): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val fragments =
      fragmentRepository
        .fetchFragmentsSync(processingType)
        .map(s => ProcessName(s.canonical.id) -> s.canonical)
        .toMap
        .get _
    pl.touk.nussknacker.engine.compile.FragmentResolver(fragments).resolve(process)
  }

}
