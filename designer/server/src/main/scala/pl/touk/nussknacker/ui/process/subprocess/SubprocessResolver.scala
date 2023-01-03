package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess, category: Category): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val versions = process.metaData.subprocessVersions.mapValuesNow(VersionId(_))
    val subprocesses = subprocessRepository.loadSubprocesses(versions, category).map(_.canonical)
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocesses).resolve(process)
  }

}
