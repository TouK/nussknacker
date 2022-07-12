package pl.touk.nussknacker.engine.lite.components

import org.apache.avro.data.TimeConversions.TimestampMicrosConversion
import org.apache.avro.{LogicalTypes, Schema}
import pl.touk.nussknacker.engine.avro.AvroUtils

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, LocalTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Random

object AvroTestData {

  private implicit class SchemasType(schemas: Seq[Schema]) {
      def toType: String = schemas.toList match {
        case head :: Nil => head.toString()
        case list => s"""[${list.map(_.toString()).mkString(",")}]"""
      }
  }

  implicit class LocalTimeOutput(time: LocalTime) {
    //See org.apache.avro.date.TimeConversions.TimeMillisConversion
    def toMillis: Int = TimeUnit.NANOSECONDS.toMillis(time.toNanoOfDay).toInt

    //See org.apache.avro.date.TimeConversions.TimeMicrosConversion
    def toMicros: Long = TimeUnit.NANOSECONDS.toMicros(time.toNanoOfDay)

  }

  implicit class InstantOutput(time: Instant) {
    private val InstantConverter = new TimestampMicrosConversion

    private val LongSchema = Schema.create(Schema.Type.LONG)

    def toMicros: Long = InstantConverter.toLong(time, LongSchema, LogicalTypes.timestampMicros())

    //See org.apache.avro.date.TimeConversions.TimestampMicrosConversion.fromLong
    def toMicrosFromEpoch: Long = toMicros / 1000000L

    //See org.apache.avro.date.TimeConversions.TimestampMicrosConversion.fromLong
    def toNanoAdjustment: Long = toMicros % 1000000L * 1000L
  }

  val RecordFieldName: String = "field"

  //Sample data
  val sampleInteger: Int = 1
  val sampleFloat: Float = 13.3.toFloat
  val sampleDouble: Double = 15.5
  val sampleLong: Long = Integer.MAX_VALUE.toLong + 1
  val sampleString: String = "lcl"
  val sampleBoolean: Boolean = true
  val sampleBytes: Array[Byte] = sampleString.getBytes("UTf-8")
  val samplePriceRecord: Map[String, Double] = Map("price" -> sampleDouble)
  val sampleIntegerArray: List[Int] = List(1, 2)
  val sampleArrayInArray: List[List[Int]] = List(sampleIntegerArray)
  val sampleArrayWithRecord: List[Map[String, Double]] = List(samplePriceRecord)
  val sampleMapInts: Map[String, Int] = Map("tax" -> 7)
  val sampleMapOfMapsInts: Map[String, Map[String, Int]] = Map("first" -> sampleMapInts)
  val sampleMapOfRecords: Map[String, Map[String, Double]] = Map("first" -> samplePriceRecord)
  val sampleNestedRecord: Map[String, Map[String, Double]] = Map("sub" -> samplePriceRecord)
  val defaultNestedRecordV2: Map[String, Object] = Map("sub" -> samplePriceRecord, "str" -> "sample")
  val sampleNestedRecordV2: Map[String, Object] = Map("sub" -> Map("price" -> sampleDouble, "currency" -> "PLN"), "str" -> "sample")
  val sampleEnum = "SPADES"
  val sampleEnumV2 = "HEARTS2"
  val sampleFixed = "098f6bcd4621d373cade4e832627b4f6"
  val sampleFixedV2 = "7551140914207932"
  val sampleUUID: UUID = UUID.randomUUID()
  val sampleDecimal: java.math.BigDecimal = new java.math.BigDecimal(1).setScale(2)
  val sampleDate: LocalDate = LocalDate.now()
  val sampleMillisLocalTime: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MILLIS)
  val sampleMicrosLocalTime: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MICROS)
  val sampleMillisInstant: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val sampleMicrosInstant: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

  //Primitive schemas
  val nullSchema: Schema = AvroUtils.parseSchema("""{"type":"null"}""")

  val integerSchema: Schema = AvroUtils.parseSchema("""{"type":"int"}""")

  val longSchema: Schema = AvroUtils.parseSchema("""{"type":"long"}""")

  val doubleSchema: Schema = AvroUtils.parseSchema("""{"type":"double"}""")

  val floatSchema: Schema = AvroUtils.parseSchema("""{"type":"float"}""")

  val stringSchema: Schema = AvroUtils.parseSchema("""{"type":"string"}""")

  val booleanSchema: Schema = AvroUtils.parseSchema("""{"type":"boolean"}""")

  val bytesSchema: Schema = AvroUtils.parseSchema("""{"type":"bytes"}""")

  //Record with primitives
  val recordInteger: Schema = buildRecord(buildField(RecordFieldName, None, integerSchema))

  val recordBoolean: Schema = buildRecord(buildField(RecordFieldName, None, booleanSchema))

  val recordLong: Schema = buildRecord(buildField(RecordFieldName, None, longSchema))

  val recordPrice: Schema = buildRecord(buildField("price", None, nullSchema, doubleSchema))

  val recordStringPrice: Schema = buildRecord(buildField("price", None, nullSchema, stringSchema))

  //Union schemas
  val recordUnionOfStringInteger: Schema = buildRecord(buildField(RecordFieldName, None, stringSchema, integerSchema))

  val recordMaybeBoolean: Schema = buildRecord(buildField(RecordFieldName, None, nullSchema, booleanSchema))

  //Avro array schemas
  val arrayOfStrings: Schema = buildArray(stringSchema)

  val recordWithArrayOfStrings: Schema = buildRecord(buildField(RecordFieldName, None, arrayOfStrings))

  val arrayOfNumbers: Schema = buildArray(integerSchema, doubleSchema)

  val recordWithArrayOfNumbers: Schema = buildRecord(buildField(RecordFieldName, None, arrayOfNumbers))

  val recordWithMaybeArrayOfNumbers: Schema = buildRecord(buildField(RecordFieldName, None, nullSchema, arrayOfNumbers))

  val recordWithOptionalArrayOfNumbers: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, arrayOfNumbers
  ))

  val recordOptionalArrayOfArraysStrings: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildArray(nullSchema, arrayOfStrings)
  ))

  val recordOptionalArrayOfArraysNumbers: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildArray(nullSchema, arrayOfNumbers)
  ))

  val recordOptionalArrayOfRecords: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildArray(nullSchema, recordPrice)
  ))

  //Avro map schemas
  val mapOfStrings: Schema = buildMap(nullSchema, stringSchema)

  val recordMapOfStrings: Schema = buildRecord(buildField(RecordFieldName, None,
    mapOfStrings
  ))

  val mapOfInts: Schema = buildMap(nullSchema, integerSchema)

  val recordMapOfInts: Schema = buildRecord(buildField(RecordFieldName, None,
    mapOfInts
  ))

  val recordMaybeMapOfInts: Schema = buildRecord(buildField(RecordFieldName, None,
    nullSchema, mapOfInts
  ))

  val recordOptionalMapOfInts: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, mapOfInts
  ))

  val recordMapOfMapsStrings: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildMap(nullSchema, mapOfStrings)
  ))

  val recordOptionalMapOfMapsInts: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildMap(nullSchema, mapOfInts)
  ))

  val recordOptionalMapOfStringRecords: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildMap(nullSchema, recordStringPrice)
  ))

  val recordOptionalMapOfRecords: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildMap(nullSchema, recordPrice)
  ))

  //Avro record schemas
  val nestedRecordWithStringPriceSchema: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildRecord(buildField("sub", Some("null"),
      nullSchema, recordStringPrice
    ))
  ))


  val nestedRecordSchema: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildRecord(buildField("sub", Some("null"),
      nullSchema, recordPrice
    ))
  ))

  val subV2RecordSchema: Schema = Schema.createUnion(
    nullSchema, buildRecord(
    buildField("price", None, nullSchema, doubleSchema),
    buildField("currency", Some("USD"), stringSchema),
  ))

  val nestedRecordSchemaV2: Schema = buildRecord(buildField(RecordFieldName, Some("null"),
    nullSchema, buildRecord(
      buildField("sub", Some("null"), subV2RecordSchema),
      buildField("str", None, stringSchema)
    )
  ))

  //Avro other schemas
  val recordStringSchema: Schema = buildRecord(buildField(RecordFieldName, None, stringSchema))

  val baseEnum: Schema = AvroUtils.parseSchema("""{"name":"Suit","type":"enum","symbols":["SPADES","HEARTS","DIAMONDS","CLUBS"]}""")

  val recordEnumSchema: Schema = buildRecord(buildField(RecordFieldName, None, baseEnum))

  val recordEnumSchemaV2: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"name":"Suit","type":"enum","symbols":["SPADES","HEARTS2","DIAMONDS2","CLUBS2"]}""")
  ))

  val baseFixed: Schema = AvroUtils.parseSchema("""{"name":"MD5","type":"fixed","size":32}""")

  val recordFixedSchema: Schema = buildRecord(buildField(RecordFieldName, None, baseFixed))

  val recordFixedSchemaV2: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"name":"short","type":"fixed","size":16}""")
  ))

  val recordUUIDSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"string","logicalType":"uuid"}""")
  ))

  val recordBytesSchema: Schema = buildRecord(buildField(RecordFieldName, None, bytesSchema))

  val recordDecimalSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"bytes","logicalType":"decimal","precision":4,"scale":2}""")
  ))

  val recordDateSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"int","logicalType":"date"}""")
  ))

  val recordTimeMillisSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"int","logicalType":"time-millis"}""")
  ))

  val recordTimeMicrosSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"long","logicalType":"time-micros"}""")
  ))

  val recordTimestampMillisSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"long","logicalType":"timestamp-millis"}""")
  ))

  val recordTimestampMicrosSchema: Schema = buildRecord(buildField(RecordFieldName, None,
    AvroUtils.parseSchema("""{"type":"long","logicalType":"timestamp-micros"}""")
  ))

  private def buildRecord(fields: String*): Schema = AvroUtils.parseSchema(
    s"""
       |{
       |  "type": "record",
       |  "name": "AvroTestRecord_${Math.abs(Random.nextLong())}",
       |  "fields": [
       |    ${fields.mkString(",")}
       |  ]
       |}
       |""".stripMargin)

  private def buildField(name: String, default: Option[String], schemas: Schema*): String = {
    def defaultProperty(value: Any) = s""", "default": $value"""

    val defaultElement = default match {
      case Some(str: String) if str != "null" => defaultProperty(s""""$str"""")
      case Some(any) => defaultProperty(any)
      case None => ""
    }

    s"""{"name": "$name", "type": ${schemas.toType} $defaultElement}""".stripMargin
  }

  private def buildArray(schemas: Schema*): Schema = AvroUtils.parseSchema(s"""{"type":"array", "items": ${schemas.toType}}""")

  private def buildMap(schemas: Schema*): Schema = AvroUtils.parseSchema(s"""{"type":"map", "values": ${schemas.toType}}""")

}
