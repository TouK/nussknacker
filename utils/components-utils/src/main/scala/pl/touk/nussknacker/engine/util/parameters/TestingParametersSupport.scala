package pl.touk.nussknacker.engine.util.parameters

object TestingParametersSupport {

  private val delimiter: Char = '.'

  def joinWithDelimiter(key1: String, key2: String) = s"$key1$delimiter$key2"

  // Used to un-flat map with concat name eg. { a.b -> _ } => { a -> { b -> _ } }. Reverse kinda joinWithDelimiter
  def unflattenParameters(flatMap: Map[String, AnyRef]): Map[String, AnyRef] = {
    flatMap.foldLeft(Map.empty[String, AnyRef]) { case (result, (key, value)) =>
      if (key.contains(delimiter)) {
        val (parentKey, childKey) = key.span(_ != delimiter)
        val parentValue = result.getOrElse(parentKey, Map.empty[String, AnyRef]).asInstanceOf[Map[String, AnyRef]]
        val childMap    = unflattenParameters(Map(childKey.drop(1) -> value))
        result + (parentKey -> (parentValue ++ childMap))
      } else {
        result + (key -> value)
      }
    }
  }
}
