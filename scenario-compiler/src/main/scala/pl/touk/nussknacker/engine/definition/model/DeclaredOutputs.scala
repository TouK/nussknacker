package pl.touk.nussknacker.engine.definition.model

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.component.ComponentOutput

final case class DeclaredOutputs(outputs: NonEmptyList[ComponentOutput]) {

  def declares(outputName: String): Boolean = outputs.exists(_.name == outputName)

  def undeclaredAmong(outputNames: Set[String]): Set[String] = outputNames.filterNot(declares)

  def declaresNoAdditional: Boolean = outputs.tail.isEmpty

  /** The wired names among the declared additional outputs - the ones only a multi-output implementation can route. */
  def wiredAdditionalAmong(outputNames: Set[String]): Set[String] =
    outputNames.intersect(outputs.tail.map(_.name).toSet)

}
