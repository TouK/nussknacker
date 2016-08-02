package pl.touk.esp.engine.api

case class InterpretationResult(reference: Option[PartReference], output: Any, finalContext: Context)
