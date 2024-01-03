package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.util.{Failure, Success, Try}

class ScenarioResolver(fragmentResolver: FragmentResolver) {

  def resolveScenario(canonical: CanonicalProcess, processingType: ProcessingType)(
      implicit user: LoggedUser
  ): Try[CanonicalProcess] =
    toTry(fragmentResolver.resolveFragments(canonical.withoutDisabledNodes, processingType))

  private def toTry[E, A](validated: ValidatedNel[E, A]) =
    validated.map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))

}
