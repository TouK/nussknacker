package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.InaccurateSubprocessDefinitionExtractor
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess, category: Category): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val subprocesses = subprocessRepository.loadSubprocesses(Map.empty, category).map(s => s.canonical.id -> s.canonical).toMap.get _
    // Here is a little trick. For purpose of subprocess resolution we don't need accurate Parameters definition because on
    // this stage we don't check them. There are checked during ProcessValidation/Compilation. We don't want to pass here accurate
    // SubprocessDefinitionExtractor because it cause deadlock during loading EmbeddedDeploymentManager's scenarios.
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocesses, InaccurateSubprocessDefinitionExtractor).resolve(process)
  }

}
