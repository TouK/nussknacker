package pl.touk.nussknacker.engine.marshall

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess

object ScenarioParser {

  def parseUnsafe(canonicalJson: String): EspProcess = parse(canonicalJson) match {
    case Valid(p) => p
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
  }

  def parse(canonicalJson: String): Validated[NonEmptyList[ProcessCompilationError], EspProcess] = ProcessMarshaller
    .fromJson(canonicalJson)
    .toValidatedNel[ProcessCompilationError, CanonicalProcess]
    .andThen(ProcessCanonizer.uncanonize)

}
