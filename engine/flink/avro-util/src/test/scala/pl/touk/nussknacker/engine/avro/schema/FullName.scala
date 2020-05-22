package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}

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

object FullNameV1 extends TestSchema {
  val stringSchema: String =
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro.schema",
      |  "name": "FullName1",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin

  val exampleData: Map[String, Any] = Map("first" -> "Lucas", "last" -> "C")
}

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

object FullNameV2 extends TestSchema {
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

  val exampleData: Map[String, Any] = FullNameV1.exampleData ++ Map("middle" -> "None")
}
