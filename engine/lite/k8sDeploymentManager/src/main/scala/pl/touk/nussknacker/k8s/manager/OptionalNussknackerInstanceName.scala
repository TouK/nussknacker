package pl.touk.nussknacker.k8s.manager

import net.ceedubs.ficus.Ficus
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

class OptionalNussknackerInstanceName private (val valueOpt: Option[String]) {
  def isEmpty: Boolean = valueOpt.isEmpty

  def objectNameWithoutSanitization(mainObjectNamePart: String): String =
    instanceNamePrefix + mainObjectNamePart

  def instanceNamePrefix: String = valueOpt.map(_ + "-").getOrElse("")
}

object OptionalNussknackerInstanceName {
  val empty: OptionalNussknackerInstanceName = new OptionalNussknackerInstanceName(None)

  // This method is for unit tests purpose only, for production usage, this name is parsed from configuration
  def forInstanceName(value: String): OptionalNussknackerInstanceName = new OptionalNussknackerInstanceName(Some(value))

  implicit val valueReader: ValueReader[OptionalNussknackerInstanceName] =
    Ficus.optionValueReader[String].map(new OptionalNussknackerInstanceName(_))
}
