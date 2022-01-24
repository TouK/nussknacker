package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val subprocesses = subprocessRepository.loadSubprocesses(process.metaData.subprocessVersions.mapValues(VersionId(_))).map(_.canonical)
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocesses).resolve(process)
  }

}
