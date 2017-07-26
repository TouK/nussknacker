package pl.touk.nussknacker.engine.util

object Implicits {

  implicit class RichScalaMap[K <: Any, V <: Any](m: Map[K, V]) {

    def mapValuesNow[VV](f: V => VV): Map[K, VV] = m.map { case (k, v) => k -> f(v) }
  }

  implicit class RichString(s: String) {
    def safeToOption: Option[String] = {
      if (s == null || s == "") None
      else Some(s)
    }
  }
}
