package pl.touk.nussknacker.engine.api.process

sealed trait RunMode

object RunMode {

  case object Engine extends RunMode

  case object Test extends RunMode

  case object Verification extends RunMode

  case object ServiceQuery extends RunMode

  case object Definition extends RunMode
}
