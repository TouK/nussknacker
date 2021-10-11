package pl.touk.nussknacker.engine.canonize

import pl.touk.nussknacker.engine.api.context.ProcessUncanonizationError
import pl.touk.nussknacker.engine.graph.EspProcess

private[engine] trait MaybeArtificialExtractor[A] {
  def get(errors: List[ProcessUncanonizationError], rawValue: A): A
}

private[engine] object MaybeArtificialExtractor {
  implicit val espProcess: MaybeArtificialExtractor[EspProcess] = new MaybeArtificialExtractor[EspProcess] {
    override def get(errors: List[ProcessUncanonizationError], rawValue: EspProcess): EspProcess = {
      // todo: we should probably remove artificial nodes by dummy id if there is an error
      rawValue
    }
  }
}
