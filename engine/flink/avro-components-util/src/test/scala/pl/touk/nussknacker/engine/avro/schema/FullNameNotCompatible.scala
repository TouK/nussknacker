package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder

case class FullNameNotCompatible(var first: CharSequence, var middle: CharSequence, var last: CharSequence, var sex: CharSequence) extends SpecificRecordBase {
  def this() = this(null, null, null, null)

  override def getSchema: Schema = FullNameNotCompatible.schema

  override def get(field: Int): AnyRef =
    field match {
      case 0 => first
      case 1 => middle
      case 2 => last
      case 3 => sex
      case _ => throw new AvroRuntimeException("Bad index")
    }

  override def put(field: Int, value: scala.Any): Unit =
    field match {
      case 0 => first = value.asInstanceOf[CharSequence]
      case 1 => middle = value.asInstanceOf[CharSequence]
      case 2 => last = value.asInstanceOf[CharSequence]
      case 3 => sex = value.asInstanceOf[CharSequence]
      case _ => throw new AvroRuntimeException("Bad index")
    }
}

object FullNameNotCompatible extends TestSchemaWithSpecificRecord {
  final val BaseSex = "man"

  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro.schema",
      |  "name": "FullNameNotCompatible",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" },
      |    { "name": "sex", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = FullNameV2.exampleData ++ Map("sex" -> BaseSex)

  def createRecord(first: String, middle: String, last: String, sex: String): GenericData.Record =
    avroEncoder.encodeRecordOrError(Map("first" -> first, "last" -> last, "middle" -> middle, "sex" -> sex), schema)

  def createSpecificRecord(first: String, middle: String, last: String, sex: String): FullNameNotCompatible =
    new FullNameNotCompatible(first, middle, last, sex)

  lazy val specificRecord: SpecificRecordBase =
    createSpecificRecord(FullNameV1.BaseFirst, FullNameV2.BaseMiddle, FullNameV1.BaseLast, BaseSex)
}

