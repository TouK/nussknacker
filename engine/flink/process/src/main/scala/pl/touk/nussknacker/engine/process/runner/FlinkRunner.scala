package pl.touk.nussknacker.engine.process.runner

import java.nio.charset.StandardCharsets

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

trait FlinkRunner {

  protected def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      val source = scala.io.Source.fromFile(arg.substring(1), StandardCharsets.UTF_8.name())
      try source.mkString finally source.close()
    } else {
      arg
    }
    ProcessMarshaller.fromJson(canonicalJson).toValidatedNel[Any, CanonicalProcess] andThen { canonical =>
      ProcessCanonizer.uncanonize(canonical.withoutDisabledNodes)
    } match {
      case Valid(p) => p
      case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
    }
  }
}
