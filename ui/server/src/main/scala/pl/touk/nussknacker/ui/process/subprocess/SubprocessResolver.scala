package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilationError

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocessRepository.loadSubprocesses()
      .map(sp => sp.metaData.id -> sp).toMap).resolve(process)
  }

}
