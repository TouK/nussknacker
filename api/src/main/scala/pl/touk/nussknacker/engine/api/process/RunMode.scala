package pl.touk.nussknacker.engine.api.process

/**
  * Specifies the mode a node is invoked. It can be one of the following values:
  * <ul>
  * <li>Normal - process is executed inside Flink or other engine. Real implementation of node should be used. Note, this
  * mode is also used for service query, validations and test data generation.</li>
  * <li>Test - process is run in test mode to collect results for test data. Can be used to stub real implementation.</li>
  * </ul>
  */
sealed trait RunMode

object RunMode {

  case object Normal extends RunMode

  case object Test extends RunMode

}
