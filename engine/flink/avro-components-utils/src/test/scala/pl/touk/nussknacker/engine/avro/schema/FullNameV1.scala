package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder

case class FullNameV1(var first: CharSequence, var last: CharSequence) extends SpecificRecordBase {
  def this() = this(null, null)

  override def getSchema: Schema = FullNameV1.schema

  override def get(field: Int): AnyRef =
    field match {
      case 0 => first
      case 1 => last
      case _ => throw new AvroRuntimeException("Bad index")
    }

  override def put(field: Int, value: scala.Any): Unit =
    field match {
      case 0 => first = value.asInstanceOf[CharSequence]
      case 1 => last = value.asInstanceOf[CharSequence]
      case _ => throw new AvroRuntimeException("Bad index")
    }
}

object FullNameV1 extends TestSchemaWithSpecificRecord {
  final val BaseFirst = "Lucas"
  final val BaseLast = "Touk"

  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro.schema",
      |  "name": "FullNameV1",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map("first" -> BaseFirst, "last" -> BaseLast)

  def createRecord(first: String, last: String): GenericData.Record =
    avroEncoder.encodeRecordOrError(Map("first" -> first, "last" -> last), schema)

  def createSpecificRecord(first: String, last: String): FullNameV1 =
    new FullNameV1(first, last)

  lazy val specificRecord: SpecificRecordBase =
    createSpecificRecord(BaseFirst, BaseLast)
}
