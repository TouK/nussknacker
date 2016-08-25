package pl.touk.esp.ui.util

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

}
