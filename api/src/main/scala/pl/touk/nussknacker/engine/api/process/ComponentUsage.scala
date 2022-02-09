package pl.touk.nussknacker.engine.api.process

/**
  * Specifies the mode a node is used/invoked. It can be one of the following values:
  * <ul>
  * <li>EngineRuntime - component is invoked in real engine, eg. Flink.</li>
  * <li>TestRuntime - component is invoked in test mode to collect results for test data. Can be used to stub real implementation.</li>
  * <li>Validation - used when compiling and validating nodes by Designer. Components should not be invoked in this mode.</li>
  * <li>ServiceQuery - used when component (Service) is invoked by Designer in ServiceQuery.</li>
  * <li>TestDataGeneration - used when compiling, but only for purpose of generating test data. Components should not be invoked in this mode.</li>
  * </ul>
  */
sealed trait ComponentUsage

object ComponentUsage {
  case object EngineRuntime extends ComponentUsage
  case object TestRuntime extends ComponentUsage
  case object Validation extends ComponentUsage
  case object ServiceQuery extends ComponentUsage
  case object TestDataGeneration extends ComponentUsage

}
