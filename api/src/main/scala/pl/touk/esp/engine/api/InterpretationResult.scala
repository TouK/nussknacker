package pl.touk.esp.engine.api

case class InterpretationResult(reference: PartReference,
                                output: Any,
                                finalContext: Context)