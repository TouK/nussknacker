package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.fragment.{FragmentDetails, FragmentRepository}

class StubFragmentRepository(fragments: Set[FragmentDetails]) extends FragmentRepository {
  override def loadFragments(versions: Map[String, VersionId]): Set[FragmentDetails] = fragments
  override def loadFragments(versions: Map[String, VersionId], category: Category): Set[FragmentDetails] =
    loadFragments(versions).filter(_.category == category)
}
