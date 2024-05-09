package pl.touk.nussknacker.engine.util.parameters

import pl.touk.nussknacker.engine.api.parameter.ParameterName

object TestingParametersSupport {

  private val delimiter: Char = '.'

  def joinWithDelimiter(key1: String, key2: String) = s"$key1$delimiter$key2"

  // Used to un-flat map with concat name eg. { a.b -> _ } => { a -> { b -> _ } }. Reverse kinda joinWithDelimiter
  def unflattenParameters(flatParameters: Map[ParameterName, AnyRef]): Map[String, AnyRef] = {
    unflattenParametersInternal(flatParameters.map { case (ParameterName(name), value) => name -> value })
  }

  private def unflattenParametersInternal(flatParameters: Map[String, AnyRef]): Map[String, AnyRef] = {
    flatParameters
      .foldLeft(Map.empty[String, AnyRef]) { case (result, (key, value)) =>
        if (key.contains(delimiter)) {
          unflattenParameter(key, flatParameters, result)
        } else {
          result + (key -> value)
        }
      }
  }

  private def unflattenParameter(
      key: String,
      flatParameters: Map[String, AnyRef],
      result: Map[String, AnyRef]
  ): Map[String, AnyRef] = {
    val parentKey = key.takeWhile(_ != delimiter)
    result.get(parentKey) match {
      case Some(_) =>
        // parentKey was already processed because some of the previous parameters starts with the parentKey
        result
      case None =>
        val childParameters = flatParameters.collect {
          case (key, value) if key.startsWith(parentKey) =>
            val childKey = key.stripPrefix(s"$parentKey$delimiter")
            childKey -> value
        }
        val childMap = unflattenParametersInternal(childParameters)
        result + (parentKey -> childMap)
    }
  }

}
