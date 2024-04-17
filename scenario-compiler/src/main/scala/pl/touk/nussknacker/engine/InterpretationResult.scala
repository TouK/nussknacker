package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.{Context, PartReference}

case class InterpretationResult(reference: PartReference, finalContext: Context)
