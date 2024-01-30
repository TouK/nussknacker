package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.util.{Failure, Success, Try}

// The purpose of this class is to do the whole preprocessing of a scenario. The preprocessing is currently based on the two steps:
// - disabled nodes removal
// - fragments resolution
class ScenarioResolver(fragmentResolver: FragmentResolver, processingType: ProcessingType) {

  def resolveScenario(canonical: CanonicalProcess)(
      implicit user: LoggedUser
  ): Try[CanonicalProcess] =
    toTry(fragmentResolver.resolveFragments(canonical.withoutDisabledNodes, processingType))

  private def toTry[E, A](validated: ValidatedNel[E, A]) =
    validated.map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))

}
