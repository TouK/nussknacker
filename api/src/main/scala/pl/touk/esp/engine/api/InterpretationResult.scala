package pl.touk.esp.engine.api

case class InterpretationResult(reference: PartReference,
                                output: Any,
                                finalContext: Context)

object InterpretationResult {
  def apply(reference: PartReference, valueWithModifiedContext: ValueWithModifiedContext[_]): InterpretationResult = {
    InterpretationResult(reference, valueWithModifiedContext.value, valueWithModifiedContext.context)
  }
}