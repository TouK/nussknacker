package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

sealed trait SchemaId {
  def asInt: Int = this match {
    case IntSchemaId(value) => value
    case str: StringSchemaId => throw new IllegalStateException(s"Schema in a string format: $str")
  }

  def asString: String = this match {
    case int: IntSchemaId => throw new IllegalStateException(s"Schema in an int format: $int")
    case StringSchemaId(value) => value
  }

}

object SchemaId {

  def fromInt(value: Int): SchemaId = {
    if (value < 0)
      throw new IllegalArgumentException("SchemaId value must be greater than or equal to zero");
    IntSchemaId(value)
  }

  def fromString(value: String): SchemaId = StringSchemaId(value)

}

case class IntSchemaId(value: Int) extends SchemaId {
  override def toString: String = value.toString
}

case class StringSchemaId(value: String) extends SchemaId {
  override def toString: String = value
}