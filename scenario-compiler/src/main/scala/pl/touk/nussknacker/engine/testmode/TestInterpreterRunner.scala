package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.json.encoders.ResultsVariableEncoder

object TestInterpreterRunner {

  private[engine] def testResultsVariableEncoder: Any => io.circe.Json =
    ResultsVariableEncoder.createEncoderForCurrentClassLoader

}
