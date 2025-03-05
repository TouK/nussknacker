package pl.touk.nussknacker.engine.lite.components.utils

import org.apache.avro.{LogicalType, LogicalTypes, Schema}
import org.apache.avro.Schema.{Field, Type}

import scala.util.Random

object AvroSchemaCreator {

  val Null: AnyRef = Field.NULL_DEFAULT_VALUE

  import scala.jdk.CollectionConverters._

  def createRecord(fields: Field*): Schema =
    createRecord(s"AvroRecord${Math.abs(Random.nextLong())}", fields: _*)

  def createRecord(name: String, fields: Field*): Schema =
    Schema.createRecord(name, null, null, false, fields.toList.asJava)

  def createArray(schemas: Schema*): Schema =
    Schema.createArray(asSchema(schemas: _*))

  def createMap(schemas: Schema*): Schema =
    Schema.createMap(asSchema(schemas: _*))

  def createFixed(name: String, size: Int): Schema =
    Schema.createFixed(name, null, null, size)

  def createEnum(name: String, values: List[String]): Schema =
    Schema.createEnum(name, null, null, values.asJava)

  def createField(name: String, default: Any, schemas: Schema*): Field =
    new Field(name, asSchema(schemas: _*), null, default)

  def createField(name: String, schemas: Schema*): Field =
    new Field(name, asSchema(schemas: _*), null, null)

  def createLogical(logicalType: LogicalType): Schema = {
    val schema = logicalType match {
      case _ if logicalType.equals(LogicalTypes.uuid())                              => Schema.create(Type.STRING)
      case _ if logicalType.equals(LogicalTypes.date())                              => Schema.create(Type.INT)
      case _ if logicalType.equals(LogicalTypes.timeMillis())                        => Schema.create(Type.INT)
      case _ if logicalType.equals(LogicalTypes.timeMicros())                        => Schema.create(Type.LONG)
      case _ if logicalType.equals(LogicalTypes.timestampMillis())                   => Schema.create(Type.LONG)
      case _ if logicalType.equals(LogicalTypes.timestampMicros())                   => Schema.create(Type.LONG)
      case _ if classOf[LogicalTypes.Decimal].isAssignableFrom(logicalType.getClass) => Schema.create(Type.BYTES)
      case _ => throw new IllegalArgumentException(s"Unknown logical type: $logicalType.")
    }

    logicalType.addToSchema(schema)
    schema
  }

  private def asSchema(schemas: Schema*): Schema = schemas.toList match {
    case head :: Nil => head
    case list        => Schema.createUnion(list: _*)
  }

}
