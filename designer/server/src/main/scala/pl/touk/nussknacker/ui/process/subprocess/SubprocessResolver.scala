package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.SubprocessDefinitionExtractor
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess, category: Category, processConfig: Config, classLoader: ClassLoader): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val subprocesses = subprocessRepository.loadSubprocesses(Map.empty, category).map(s => s.canonical.id -> s.canonical).toMap.get _
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocesses, SubprocessDefinitionExtractor(processConfig, classLoader)).resolve(process)
  }

}
