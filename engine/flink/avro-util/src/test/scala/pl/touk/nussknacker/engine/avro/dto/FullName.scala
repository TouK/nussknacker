package pl.touk.nussknacker.engine.avro.dto

import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, Schema}
import pl.touk.nussknacker.engine.avro.AvroUtils

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

object FullNameV1 {
  val schema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro.dto",
      |  "name": "FullName1",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin)
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

object FullNameV2 {
  val schema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro.dto",
      |  "name": "FullNameV2",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "middle", "type": ["null", "string"], "default": null },
      |    { "name": "last", "type": "string" }
      |  ]
      |}
    """.stripMargin)
}
