package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.compiledgraph.CompiledTest
import pl.touk.nussknacker.engine.graph.Test

class TestCompiler {

  def compile(test: Test, typing: Map[String, NodeTypingInfo]): ValidatedNel[ProcessCompilationError, CompiledTest] = {
    ???
  }

}
