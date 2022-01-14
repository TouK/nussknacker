package pl.touk.nussknacker.engine.marshall

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess

object ScenarioParser {

  def parseUnsafe(graphProcess: GraphProcess): EspProcess = {
    parse(graphProcess) match {
      case Valid(p) => p
      case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
    }
  }

  def parse(graphProcess: GraphProcess): Validated[NonEmptyList[ProcessCompilationError], EspProcess] =
    ProcessMarshaller
      .fromGraphProcess(graphProcess)
      .toValidatedNel[ProcessCompilationError, CanonicalProcess]
      .andThen(ProcessCanonizer.uncanonize)

  def toGraphProcess(process: EspProcess): GraphProcess =
    ProcessMarshaller.toGraphProcess(ProcessCanonizer.canonize(process))

}

