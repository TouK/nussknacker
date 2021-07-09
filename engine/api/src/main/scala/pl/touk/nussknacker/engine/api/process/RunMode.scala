package pl.touk.nussknacker.engine.api.process

/**
  * Specifies the mode a custom node is invoked. It can be one of the following values:
  * <ul>
  * <li>Normal - process is executed inside Flink or other engine. Real implementation should be used.</li>
  * <li>Test - process is run in test mode to collect results for test data. Can be used to stub real implementation.</li>
  * </ul>
  */
sealed trait RunMode

object RunMode {

  case object Normal extends RunMode

  case object Test extends RunMode

}
