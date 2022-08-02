package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}

class StubSubprocessRepository(subprocesses: Set[SubprocessDetails]) extends SubprocessRepository {
  override def loadSubprocesses(versions: Map[String, VersionId]): Set[SubprocessDetails] = subprocesses
  override def loadSubprocesses(versions: Map[String, VersionId], category: Category): Set[SubprocessDetails] =
    loadSubprocesses(versions).filter(_.category == category)
}
