package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilationError

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val subprocesses = subprocessRepository.loadSubprocesses(process.metaData.subprocessVersions)
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocesses).resolve(process)
  }

}
