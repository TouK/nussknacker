package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.{Context, PartReference, ValueWithContext}

case class InterpretationResult(reference: PartReference,
                                output: Any,
                                finalContext: Context)

object InterpretationResult {

  def apply(reference: PartReference, valueWithModifiedContext: ValueWithContext[_]): InterpretationResult = {
    apply(reference, valueWithModifiedContext.value, valueWithModifiedContext.context)
  }



}