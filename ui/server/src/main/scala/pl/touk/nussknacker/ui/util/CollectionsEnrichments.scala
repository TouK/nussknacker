package pl.touk.nussknacker.ui.util

object CollectionsEnrichments {

  implicit class RichListOfTuples[L,R](self: List[(L,R)]) {
    def flatGroupByKey: Map[L, List[R]] = {
      self.groupBy {
        case (key, value) => key
      }.map {
        case (key, zippedValues) =>
          val unzippedValues = zippedValues.map {
            case (_, value) => value
          }
          key -> unzippedValues
      }
    }
  }

  implicit class RichIterableMap[K,V](m: Map[K, Iterable[V]]) {
    def sequenceMap: Map[V, Iterable[K]] = {
      m.map { case (k, values) =>
        values.map(v => v -> k)
      }.toList.flatten.groupBy(_._1).mapValues(_.map(_._2))
    }
  }

  implicit class SafeString(s: String) {
    def safeValue = {
      if (s == null || s == "") None else Some(s)
    }
  }

}
