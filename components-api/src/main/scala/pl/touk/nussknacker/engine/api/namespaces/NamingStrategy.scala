package pl.touk.nussknacker.engine.api.namespaces

// TODO: document rename and removal of SPI
case class NamingStrategy(namespace: Option[String]) {

  def prepareName(name: String): String = {
    namespace match {
      case Some(value) => s"${value}_$name"
      case None        => name
    }
  }

  def decodeName(name: String): Option[String] = {
    namespace match {
      case Some(ns) =>
        val pattern = s"${ns}_(.*)".r
        name match {
          case pattern(originalName) => Some(originalName)
          case _                     => None
        }
      case None => Some(name)
    }
  }

}
