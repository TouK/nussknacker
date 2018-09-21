package pl.touk.nussknacker.engine.api.typed

trait TypedObject {

  def containsField(name: String): Boolean

  def field(name: String): Any

}

case class TypedMap(fields: Map[String, Any]) extends TypedObject {

  override def containsField(name: String): Boolean = fields.contains(name)

  override def field(name: String): Any = fields(name)

}