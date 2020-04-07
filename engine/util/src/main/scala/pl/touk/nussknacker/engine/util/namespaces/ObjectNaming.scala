package pl.touk.nussknacker.engine.util.namespaces

trait ObjectNaming {
  def prepareName(originalName: String, namingContext: NamingContext): String
}

case class DefaultObjectNaming() extends ObjectNaming {
  override def prepareName(originalName: String, namingContext: NamingContext): String =
    originalName
}