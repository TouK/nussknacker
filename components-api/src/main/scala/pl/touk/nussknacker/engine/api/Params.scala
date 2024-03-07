package pl.touk.nussknacker.engine.api

final case class Params(nameToValueMap: Map[String, Any]) {

  def extract[T](paramName: String): Option[T] =
    rawValueExtract(paramName) match {
      case Some(null) | None => None
      case Some(value)       => Some(cast(value))
    }

  def extractUnsafe[T](paramName: String): T =
    extract[T](paramName)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))

  private def rawValueExtract(paramName: String) = nameToValueMap.get(paramName)

  private def cannotFindParamNameMessage(paramName: String) =
    s"Cannot find param name [$paramName]. Available param names: ${nameToValueMap.keys.mkString(",")}"

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {
  lazy val empty: Params = Params(Map.empty)
}
