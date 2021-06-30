package pl.touk.nussknacker.engine.api.process

/**
  * Specifies the mode process config creator is created. It can be one of the following values:
  * - Engine - process is executed inside Flink or other engine.
  * - Test - process is run in test mode to collect results for test data.
  * - Verification - process is run to verify compatibility e.g. with state of running process in older version.
  * - ServiceQuery - one of the services is queried by Nussknacker.
  * - Definition - process config creator is used extract component definitions. Components should not be invoked.
  */
sealed trait RunMode

object RunMode {

  case object Engine extends RunMode

  case object Test extends RunMode

  case object Verification extends RunMode

  case object ServiceQuery extends RunMode

  case object Definition extends RunMode
}
