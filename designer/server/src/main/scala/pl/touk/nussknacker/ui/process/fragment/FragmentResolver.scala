package pl.touk.nussknacker.ui.process.fragment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category

class FragmentResolver(fragmentRepository: FragmentRepository) {

  def resolveFragments(
      process: CanonicalProcess,
      category: Category
  ): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val fragments =
      fragmentRepository.loadFragments(Map.empty, category).map(s => s.canonical.id -> s.canonical).toMap.get _
    pl.touk.nussknacker.engine.compile.FragmentResolver(fragments).resolve(process)
  }

}
