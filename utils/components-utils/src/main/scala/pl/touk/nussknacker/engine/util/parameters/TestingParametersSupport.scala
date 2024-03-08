package pl.touk.nussknacker.engine.util.parameters

import pl.touk.nussknacker.engine.api.parameter.ParameterName

object TestingParametersSupport {

  private val delimiter: Char = '.'

  def joinWithDelimiter(key1: String, key2: String) = s"$key1$delimiter$key2"

  // Used to un-flat map with concat name eg. { a.b -> _ } => { a -> { b -> _ } }. Reverse kinda joinWithDelimiter
  def unflattenParameters(flatMap: Map[ParameterName, AnyRef]): Map[ParameterName, AnyRef] = {
    flatMap.foldLeft(Map.empty[ParameterName, AnyRef]) { case (result, (key, value)) =>
      if (key.value.contains(delimiter)) {
        val (parentKeyString, childKey) = key.value.span(_ != delimiter)
        val parentKey                   = ParameterName(parentKeyString)
        val parentValue =
          result.getOrElse(parentKey, Map.empty[ParameterName, AnyRef]).asInstanceOf[Map[ParameterName, AnyRef]]
        val childMap = unflattenParameters(Map(ParameterName(childKey.drop(1)) -> value))
        result + (parentKey -> (parentValue ++ childMap))
      } else {
        result + (key -> value)
      }
    }
  }

}
