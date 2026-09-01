package pl.touk.nussknacker.engine.api.component

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.string.Trimmed

final case class ComponentOutput(refinedName: ComponentOutput.Name) {
  val name: String              = refinedName.value
  override val toString: String = name
}

object ComponentOutput {

  type Name = Refined[String, And[NonEmpty, Trimmed]]

  val MainOutput: ComponentOutput     = ComponentOutput("main")
  val RejectedOutput: ComponentOutput = ComponentOutput("rejected")
}
