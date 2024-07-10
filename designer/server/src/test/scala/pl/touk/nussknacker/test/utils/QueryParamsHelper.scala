package pl.touk.nussknacker.test.utils

object QueryParamsHelper {

  def extractFromURLString(queryParamsPath: String): Map[String, String] =
    queryParamsPath
      .replaceFirst("^.+?\\?", "")
      .split("&")
      .map(_.split("=").toList match {
        case (key: String) :: (value: String) :: _ => (key, value)
        case value =>
          throw new IllegalArgumentException(s"Cannot parse query param with value: $value")
      })
      .toList
      .toMap

}
