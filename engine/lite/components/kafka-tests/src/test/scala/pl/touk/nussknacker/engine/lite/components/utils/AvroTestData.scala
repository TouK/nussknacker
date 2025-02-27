package pl.touk.nussknacker.engine.lite.components.utils

import org.apache.avro
import org.apache.avro.{LogicalTypes, Schema}
import org.apache.avro.Schema.Type
import org.apache.avro.data.TimeConversions.TimestampMicrosConversion
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.generic.GenericRecord
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.lite.components.utils.AvroSchemaCreator.{
  createArray,
  createEnum,
  createField,
  createFixed,
  createLogical,
  createMap,
  createRecord,
  Null
}
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, LocalTime}
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

object AvroTestData {

  implicit class LocalTimeOutput(time: LocalTime) {
    // See org.apache.avro.date.TimeConversions.TimeMillisConversion
    def toMillis: Int = TimeUnit.NANOSECONDS.toMillis(time.toNanoOfDay).toInt

    // See org.apache.avro.date.TimeConversions.TimeMicrosConversion
    def toMicros: Long = TimeUnit.NANOSECONDS.toMicros(time.toNanoOfDay)

  }

  implicit class InstantOutput(time: Instant) {
    private val InstantConverter = new TimestampMicrosConversion

    private val LongSchema = Schema.create(Schema.Type.LONG)

    def toMicros: Long = InstantConverter.toLong(time, LongSchema, LogicalTypes.timestampMicros())
  }

  val RecordFieldName: String = "field"

  // Primitive schemas
  val nullSchema: Schema = Schema.create(Type.NULL)

  val integerSchema: Schema = Schema.create(Type.INT)

  val longSchema: Schema = Schema.create(Type.LONG)

  val floatSchema: Schema = Schema.create(Type.FLOAT)

  val doubleSchema: Schema = Schema.create(Type.DOUBLE)

  val stringSchema: Schema = Schema.create(Type.STRING)

  val booleanSchema: Schema = Schema.create(Type.BOOLEAN)

  val bytesSchema: Schema = Schema.create(Type.BYTES)

  // Record with primitives
  val recordIntegerSchema: Schema = createSimpleRecord(integerSchema)

  val recordBooleanSchema: Schema = createSimpleRecord(booleanSchema)

  val recordLongSchema: Schema = createSimpleRecord(longSchema)

  val recordFloatSchema: Schema = createSimpleRecord(floatSchema)

  val recordDoubleSchema: Schema = createSimpleRecord(doubleSchema)

  val recordPriceSchema: Schema = createRecord(createField("price", nullSchema, doubleSchema))

  val recordStringPriceSchema: Schema = createRecord(createField("price", nullSchema, stringSchema))

  // Avro array schemas
  val arrayOfStringsSchema: Schema = createArray(stringSchema)

  val recordArrayOfStringsSchema: Schema = createSimpleRecord(arrayOfStringsSchema)

  val arrayOfNumbersSchema: Schema = createArray(integerSchema, doubleSchema)

  val recordArrayOfNumbersSchema: Schema = createSimpleRecord(arrayOfNumbersSchema)

  val recordMaybeArrayOfNumbersSchema: Schema = createSimpleRecord(nullSchema, arrayOfNumbersSchema)

  val recordOptionalArrayOfNumbersSchema: Schema = createSimpleRecord(Null, nullSchema, arrayOfNumbersSchema)

  val recordOptionalArrayOfArraysStringsSchema: Schema =
    createSimpleRecord(Null, nullSchema, createArray(nullSchema, arrayOfStringsSchema))

  val recordOptionalArrayOfArraysNumbersSchema: Schema =
    createSimpleRecord(Null, nullSchema, createArray(nullSchema, arrayOfNumbersSchema))

  val recordOptionalArrayOfRecordsSchema: Schema =
    createSimpleRecord(Null, nullSchema, createArray(nullSchema, recordPriceSchema))

  // Avro map schemas
  val mapOfStringsSchema: Schema = createMap(nullSchema, stringSchema)

  val recordMapOfStringsSchema: Schema = createSimpleRecord(mapOfStringsSchema)

  val mapOfIntsSchema: Schema = createMap(nullSchema, integerSchema)

  val recordMapOfIntsSchema: Schema = createRecord(createField(RecordFieldName, mapOfIntsSchema))

  val recordMaybeMapOfIntsSchema: Schema = createRecord(createField(RecordFieldName, nullSchema, mapOfIntsSchema))

  val recordOptionalMapOfIntsSchema: Schema = createSimpleRecord(Null, nullSchema, mapOfIntsSchema)

  val recordMapOfMapsStringsSchema: Schema =
    createSimpleRecord(Null, nullSchema, createMap(nullSchema, mapOfStringsSchema))

  val recordOptionalMapOfMapsIntsSchema: Schema =
    createSimpleRecord(Null, nullSchema, createMap(nullSchema, mapOfIntsSchema))

  val recordOptionalMapOfStringRecordsSchema: Schema =
    createSimpleRecord(Null, nullSchema, createMap(nullSchema, recordStringPriceSchema))

  val recordOptionalMapOfRecordsSchema: Schema =
    createSimpleRecord(Null, nullSchema, createMap(nullSchema, recordPriceSchema))

  val fakeMapRecordWithStringPrice: Schema = createSimpleRecord(recordStringPriceSchema)

  // Avro record schemas
  val baseRecordWithStringPriceSchema: Schema = createRecord(
    createField("sub", Null, nullSchema, recordStringPriceSchema)
  )

  val nestedRecordWithStringPriceSchema: Schema = createSimpleRecord(Null, nullSchema, baseRecordWithStringPriceSchema)

  val baseRecordWithPriceSchema: Schema = createRecord(createField("sub", Null, nullSchema, recordPriceSchema))

  val nestedRecordSchema: Schema = createSimpleRecord(Null, nullSchema, baseRecordWithPriceSchema)

  val nestedRecordV2FieldsSchema: Schema = Schema.createUnion(
    nullSchema,
    createRecord(
      createField(
        "sub",
        Null,
        nullSchema,
        createRecord(
          createField("price", nullSchema, doubleSchema),
          createField("currency", "USD", stringSchema),
        )
      ),
      createField("str", stringSchema)
    )
  )

  val nestedRecordSchemaV2: Schema = createSimpleRecord(Null, nestedRecordV2FieldsSchema)

  // Union schemas
  val recordUnionStringAndIntegerSchema: Schema = createSimpleRecord(stringSchema, integerSchema)

  val recordUnionStringAndBooleanSchema: Schema = createSimpleRecord(stringSchema, booleanSchema)

  val recordMaybeBooleanSchema: Schema = createSimpleRecord(nullSchema, booleanSchema)

  val recordUnionStringAndRecordIntSchema: Schema = createSimpleRecord(stringSchema, recordIntegerSchema)

  val recordUnionRecordIntAndStringSchema: Schema = createSimpleRecord(recordIntegerSchema, stringSchema)

  val recordUnionRecordLongAndStringSchema: Schema = createSimpleRecord(recordLongSchema, stringSchema)

  val recordUnionMapOfIntsAndIntSchema: Schema = createSimpleRecord(mapOfIntsSchema, integerSchema)

  val recordUnionMapOfLongsAndLongSchema: Schema = createSimpleRecord(createMap(longSchema), longSchema)

  // Avro other schemas
  val recordWithBigUnionSchema: Schema =
    createSimpleRecord(nullSchema, booleanSchema, baseRecordWithStringPriceSchema, baseRecordWithPriceSchema)

  val recordStringSchema: Schema = createSimpleRecord(stringSchema)

  val baseEnumSchema: Schema = createEnum("Suit", List("SPADES", "HEARTS", "DIAMONDS", "CLUBS"))

  val recordEnumSchema: Schema = createSimpleRecord(baseEnumSchema)

  val enumSchemaV2: Schema = createEnum("Suit", List("SPADES", "HEARTS2", "DIAMONDS2", "CLUBS2"))

  val recordEnumSchemaV2: Schema = createSimpleRecord(enumSchemaV2)

  val baseFixedSchema: Schema = createFixed("md5", size = 32)

  val recordFixedSchema: Schema = createSimpleRecord(baseFixedSchema)

  val recordFixedSchemaV2: Schema = createSimpleRecord(createFixed("short", size = 16))

  val recordUUIDSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.uuid()))

  val recordDecimalSchema: Schema = createSimpleRecord(
    AvroUtils.parseSchema("""{"type":"bytes","logicalType":"decimal","precision":4,"scale":2}""")
  )

  val recordDateSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.date()))

  val recordTimeMillisSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timeMillis()))

  val recordTimeMicrosSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timeMicros()))

  val recordTimestampMillisSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timestampMillis()))

  val recordTimestampMicrosSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timestampMicros()))

  // Sample data
  val sampleInteger: Int               = 1
  val sampleFloat: Float               = 13.3.toFloat
  val sampleDouble: Double             = 15.5
  val sampleLong: Long                 = Integer.MAX_VALUE.toLong + 1
  val sampleString: String             = "lcl"
  val sampleBoolean: Boolean           = true
  val sampleBytes: Array[Byte]         = sampleString.getBytes(StandardCharsets.UTF_8)
  val samplePriceRecord: GenericRecord = AvroUtils.createRecord(recordPriceSchema, Map("price" -> sampleDouble))

  val typedInt: typing.TypingResult    = Typed.fromInstance(sampleInteger)
  val typedLong: typing.TypingResult   = Typed.fromInstance(sampleLong)
  val typedFloat: typing.TypingResult  = Typed.fromInstance(sampleFloat)
  val typedDouble: typing.TypingResult = Typed.fromInstance(sampleDouble)
  val typedStr: typing.TypingResult    = Typed.fromInstance(sampleString)
  val typedBool: typing.TypingResult   = Typed.fromInstance(sampleBoolean)

  val sampleNestedRecord: GenericRecord =
    AvroUtils.createRecord(nestedRecordSchema, Map(RecordFieldName -> Map("sub" -> samplePriceRecord)))

  val sampleNestedRecordV2: GenericRecord = AvroUtils.createRecord(
    nestedRecordSchemaV2,
    Map(RecordFieldName -> Map("sub" -> Map("price" -> sampleDouble, "currency" -> "PLN"), "str" -> "sample"))
  )

  val sampleUnionStringAndRecordInt: GenericRecord = AvroUtils.createRecord(
    recordUnionStringAndRecordIntSchema,
    Map(RecordFieldName -> Map(RecordFieldName -> sampleInteger))
  )

  val sampleUnionRecordIntAndString: GenericRecord = AvroUtils.createRecord(
    recordUnionRecordIntAndStringSchema,
    Map(RecordFieldName -> Map(RecordFieldName -> sampleInteger))
  )

  val sampleUnionRecordLongAndString: GenericRecord = AvroUtils.createRecord(
    recordUnionRecordLongAndStringSchema,
    Map(RecordFieldName -> Map(RecordFieldName -> sampleInteger.toLong))
  )

  val sampleMapOfIntsAndInt: GenericRecord =
    AvroUtils.createRecord(recordMapOfIntsSchema, Map(RecordFieldName -> Map("key" -> sampleInteger)))

  val sampleUnionMapOfIntsAndInt: GenericRecord =
    AvroUtils.createRecord(recordUnionMapOfIntsAndIntSchema, Map(RecordFieldName -> Map("key" -> sampleInteger)))

  val sampleUnionMapOfLongsAndLong: GenericRecord = AvroUtils.createRecord(
    recordUnionMapOfLongsAndLongSchema,
    Map(RecordFieldName -> Map("key" -> sampleInteger.toLong))
  )

  val sampleEnumString = "SPADES"
  val sampleEnum       = new EnumSymbol(baseEnumSchema, sampleEnumString)

  val sampleStrEnumV2                     = "HEARTS2"
  val typedStrEnumV2: typing.TypingResult = Typed.fromInstance(sampleStrEnumV2)
  val sampleEnumV2                        = new EnumSymbol(enumSchemaV2, sampleStrEnumV2)

  val sampleStrFixed = "098f6bcd4621d373cade4e832627b4f6"
  val sampleFixed    = new Fixed(baseFixedSchema, sampleStrFixed.getBytes(StandardCharsets.UTF_8))

  val sampleStrFixedV                     = "7551140914207932"
  val typeStrFixedV2: typing.TypingResult = Typed.fromInstance(sampleStrFixedV)
  val sampleFixedV2 = new Fixed(recordFixedSchemaV2, sampleStrFixedV.getBytes(StandardCharsets.UTF_8))

  val sampleUUID: UUID                    = UUID.randomUUID()
  val sampleDecimal: java.math.BigDecimal = new java.math.BigDecimal(1).setScale(2)
  val sampleDate: LocalDate               = LocalDate.now()
  val sampleMillisLocalTime: LocalTime    = LocalTime.now().truncatedTo(ChronoUnit.MILLIS)
  val sampleMicrosLocalTime: LocalTime    = LocalTime.now().truncatedTo(ChronoUnit.MICROS)
  val sampleMillisInstant: Instant        = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val sampleMicrosInstant: Instant        = Instant.now().truncatedTo(ChronoUnit.MICROS)

  private def createSimpleRecord(schema: Schema*) = createRecord(createField(RecordFieldName, schema: _*))

  private def createSimpleRecord(default: Any, schema: Schema*) = createRecord(
    createField(RecordFieldName, default, schema: _*)
  )

  val personSchema: avro.Schema = AvroUtils.parseSchema(s"""{
       |  "type": "record",
       |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
       |  "name": "FullName",
       |  "fields": [
       |    { "name": "first", "type": "string" },
       |    { "name": "last", "type": "string" },
       |    { "name": "age", "type": "int" }
       |  ]
       |}
    """.stripMargin)

}
