package pl.touk.nussknacker.engine.api.namespaces

trait ObjectNaming {
  def prepareName(originalName: String, namingContext: NamingContext): String
}

case object DefaultObjectNaming extends ObjectNaming {
  override def prepareName(originalName: String, namingContext: NamingContext): String =
    originalName
}