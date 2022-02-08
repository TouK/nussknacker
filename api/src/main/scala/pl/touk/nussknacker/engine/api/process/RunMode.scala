package pl.touk.nussknacker.engine.api.process

/**
  * Specifies the mode a node is invoked. It can be one of the following values:
  * <ul>
  * <li>Normal - used when process is executed inside Flink or other engine, and also for service query, validations and test data generation.
  * In this mode real implementation of node should be used.</li>
  * <li>Test - process is run in test mode to collect results for test data. Can be used to stub real implementation.</li>
  * </ul>
  */
sealed trait RunMode

object RunMode {

  sealed trait Normal extends RunMode

  case object Engine extends Normal
  case object Validation extends Normal
  case object ServiceQuery extends Normal
  case object TestDataGeneration extends Normal

  case object Test extends RunMode

}
