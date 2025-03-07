package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

// The purpose of this class is to do the whole preprocessing of a scenario. The preprocessing is currently based on the two steps:
// - disabled nodes removal
// - fragments resolution
class ScenarioResolver(fragmentResolver: FragmentResolver, processingType: ProcessingType) {

  def resolveScenario(canonical: CanonicalProcess)(
      implicit user: LoggedUser,
      executionContext: ExecutionContext
  ): Future[ValidatedNel[ProcessCompilationError, CanonicalProcess]] =
    fragmentResolver.resolveFragmentsAsync(canonical.withoutDisabledNodes, processingType)

}
