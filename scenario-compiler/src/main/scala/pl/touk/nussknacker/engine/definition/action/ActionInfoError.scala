package pl.touk.nussknacker.engine.definition.action

sealed trait ActionInfoError
case object CannotCompileScenario extends ActionInfoError
