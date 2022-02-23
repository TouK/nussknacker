package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.generic.GenericData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder

case class FullNameV2(var first: CharSequence, var middle: CharSequence, var last: CharSequence) extends SpecificRecordBase {
  def this() = this(null, null, null)

  override def getSchema: Schema = FullNameV2.schema

  override def get(field: Int): AnyRef =
    field match {
      case 0 => first
      case 1 => middle
      case 2 => last
      case _ => throw new AvroRuntimeException("Bad index")
    }

  override def put(field: Int, value: scala.Any): Unit =
    field match {
      case 0 => first = value.asInstanceOf[CharSequence]
      case 1 => middle = value.asInstanceOf[CharSequence]
      case 2 => last = value.asInstanceOf[CharSequence]
      case _ => throw new AvroRuntimeException("Bad index")
    }
}

object FullNameV2 extends TestSchemaWithSpecificRecord {
  final val BaseMiddle = "SP"

  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro.schema",
      |  "name": "FullNameV2",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = FullNameV1.exampleData ++ Map("middle" -> BaseMiddle)

  def createRecord(first: String, middle: String, last: String): GenericData.Record =
    avroEncoder.encodeRecordOrError(Map("first" -> first, "last" -> last, "middle" -> middle), schema)

  def createSpecificRecord(first: String, middle: String, last: String): FullNameV2 =
    new FullNameV2(first, middle, last)

  lazy val specificRecord: SpecificRecordBase =
    createSpecificRecord(FullNameV1.BaseFirst, BaseMiddle, FullNameV1.BaseLast)

  def migratedGenericRecordFromV1: GenericData.Record =
    avroEncoder.encodeRecordOrError(Map("first" -> FullNameV1.BaseFirst, "last" -> FullNameV1.BaseLast, "middle" -> null), schema)
}
