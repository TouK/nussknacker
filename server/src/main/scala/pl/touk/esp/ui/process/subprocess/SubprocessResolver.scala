package pl.touk.esp.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.ProcessCompilationError

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    pl.touk.esp.engine.compile.SubprocessResolver(subprocessRepository.loadSubprocesses()
      .map(sp => sp.id -> sp).toMap).resolve(process)
  }

}
