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
sealed trait ComponentUseCase

object ComponentUseCase {
  case object EngineRuntime extends ComponentUseCase
  case object TestRuntime extends ComponentUseCase
  case object Validation extends ComponentUseCase
  case object ServiceQuery extends ComponentUseCase
  case object TestDataGeneration extends ComponentUseCase
  case object Mock extends ComponentUseCase

}
