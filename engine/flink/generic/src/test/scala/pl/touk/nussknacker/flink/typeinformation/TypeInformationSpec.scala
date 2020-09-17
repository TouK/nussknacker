package pl.touk.nussknacker.flink.typeinformation

import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.core.memory.DataOutputSerializer
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.process.typeinformation.TypingResultAwareTypeInformationDetection
import pl.touk.nussknacker.engine.process.util.Serializers

class TypeInformationSpec extends FunSuite with Matchers {

  val typeInformationDetection = new TypingResultAwareTypeInformationDetection(Set())

  test("serializing") {

    //serialize[Int](11)
    //serialize[String]("alamakota", Typed[String])
    serialize[Sample1](Sample1("ala", 11), Typed[Sample1])
    serialize[Map[String, Any]](Map("" -> Sample1("ala", 11)))

    //serialize[Map[String, Any]](Map("name" -> "ala", "data" -> 11))
    val veryLongName = "veeeeeeeeeeeerrrrrrrrrrrrrrrrrrLooooooooooooooong"
    serialize[Map[String, Any]](Map(veryLongName -> "ala", "data" -> 11), TypedObjectTypingResult(Map(veryLongName -> Typed[String], "data" -> Typed[Int])))

    //serialize[Map[String, Any]](Map("name" -> "ala", "data" -> 11, "extended" -> Sample1("hello", 444)))
    //serialize[Map[String, Any]](Map("n" -> Sample1("hello", 444)))
  }

  test("map serialization") {
    serialize(Context("id").withVariables(Map(
      "field1" -> "",
      "field2" -> 2323,
      "field3" -> "value",
      "field4" -> false,
      "field5" -> 3333L,
      "field6" -> "",
      "field7" -> "1",
      "field8" -> "2",
      "field9" -> ""
    )))(typeInformationDetection.forContext(ValidationContext(
      Map(
        "field1" -> Typed[String],
        "field2" -> Typed[Int],
        "field3" -> Typed[String],
        "field4" -> Typed[Boolean],
        "field5" -> Typed[Long],
        "field6" -> Typed[String],
        "field7" -> Typed[String],
        "field8" -> Typed[String],
        "field9" -> Typed[String]
      )
    )))
  }

  test("avro serializing") {

    val schema = SchemaBuilder.builder()
      .record("sample1")
      .fields()
      .requiredString("name")
      .requiredInt("data")
      .endRecord()
    val record = new GenericRecordBuilder(schema)
      .set("name", "ala")
      .set("data", 444).build().asInstanceOf[GenericRecord]


    serialize(record)(new GenericRecordAvroTypeInfo(schema))

  }

  def serialize[T](value: T, tr: TypingResult = Unknown)(implicit ti: TypeInformation[T]): Unit = {
    println(s"Checking for $value")
    val config = new ExecutionConfig
    Serializers.registerSerializers(config)

    def serializeWithTi[Y>:T](ti: TypeInformation[Y]): DataOutputSerializer = {
      val serializer = ti.createSerializer(config)
      val outputView = new DataOutputSerializer(1000)
      serializer.serialize(value, outputView)
      outputView
    }

    println("  Size via implicit is: " + serializeWithTi(ti).length())
    println("  Size via TypeInformation.of is: " + serializeWithTi(TypeInformation.of(value.getClass.asInstanceOf[Class[T]])).length())
    println("  Size via Any is: " + serializeWithTi(TypeInformation.of(classOf[Any])).length())
    println("  Size via TypeInformationDetection.forType is: " + serializeWithTi(typeInformationDetection.forType(tr)).length())

  }

}

case class Sample1(name: String, data: Int)