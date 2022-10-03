package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver

import scala.util.{Failure, Success, Try}

class ScenarioResolver(subprocessResolver: SubprocessResolver) {

  def resolveScenario(canonical: CanonicalProcess, category: Category): Try[CanonicalProcess] =
    toTry(subprocessResolver.resolveSubprocesses(canonical.withoutDisabledNodes, category))

  private def toTry[E, A](validated: ValidatedNel[E, A]) =
    validated.map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))

}
