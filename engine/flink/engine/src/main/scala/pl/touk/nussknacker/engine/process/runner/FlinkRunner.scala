package pl.touk.nussknacker.engine.process.runner

import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.Implicits.SourceIsReleasable

import java.nio.charset.StandardCharsets
import scala.util.Using

trait FlinkRunner {

  protected def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      Using.resource(scala.io.Source.fromFile(arg.substring(1), StandardCharsets.UTF_8.name()))(_.mkString)
    } else {
      arg
    }
    ScenarioParser.parseUnsafe(canonicalJson)
  }
}
