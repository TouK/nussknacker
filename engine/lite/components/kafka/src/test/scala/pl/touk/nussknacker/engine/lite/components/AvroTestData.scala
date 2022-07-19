package pl.touk.nussknacker.engine.lite.components

import org.apache.avro.Schema.Type
import org.apache.avro.data.TimeConversions.TimestampMicrosConversion
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.generic.GenericRecord
import org.apache.avro.{LogicalTypes, Schema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.AvroSchemaCreator._
import pl.touk.nussknacker.engine.avro.AvroUtils

import java.nio.charset.StandardCharsets
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate, LocalTime}
import java.util
import java.util.UUID
import java.util.concurrent.TimeUnit

object AvroTestData {

  import collection.JavaConverters._

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

  //Primitive schemas
  val nullSchema: Schema = Schema.create(Type.NULL)

  val integerSchema: Schema = Schema.create(Type.INT)

  val longSchema: Schema = Schema.create(Type.LONG)

  val floatSchema: Schema = Schema.create(Type.FLOAT)

  val doubleSchema: Schema = Schema.create(Type.DOUBLE)

  val stringSchema: Schema = Schema.create(Type.STRING)

  val booleanSchema: Schema = Schema.create(Type.BOOLEAN)

  val bytesSchema: Schema = Schema.create(Type.BYTES)

  //Record with primitives
  val recordIntegerSchema: Schema = createSimpleRecord(integerSchema)

  val recordBooleanSchema: Schema = createSimpleRecord(booleanSchema)

  val recordLongSchema: Schema = createSimpleRecord(longSchema)

  val recordPriceSchema: Schema = createRecord(createField("price", nullSchema, doubleSchema))

  val recordStringPriceSchema: Schema = createRecord(createField("price", nullSchema, stringSchema))

  //Union schemas
  val recordUnionOfStringInteger: Schema = createSimpleRecord(stringSchema, integerSchema)

  val recordMaybeBoolean: Schema = createSimpleRecord(nullSchema, booleanSchema)

  //Avro array schemas
  val arrayOfStrings: Schema = createArray(stringSchema)

  val recordWithArrayOfStrings: Schema = createSimpleRecord(arrayOfStrings)

  val arrayOfNumbers: Schema = createArray(integerSchema, doubleSchema)

  val recordWithArrayOfNumbers: Schema = createSimpleRecord(arrayOfNumbers)

  val recordWithMaybeArrayOfNumbers: Schema = createSimpleRecord(nullSchema, arrayOfNumbers)

  val recordWithOptionalArrayOfNumbers: Schema = createSimpleRecord(Null, nullSchema, arrayOfNumbers)

  val recordOptionalArrayOfArraysStrings: Schema = createSimpleRecord(Null, nullSchema, createArray(nullSchema, arrayOfStrings))

  val recordOptionalArrayOfArraysNumbers: Schema = createSimpleRecord(Null, nullSchema, createArray(nullSchema, arrayOfNumbers))

  val recordOptionalArrayOfRecords: Schema = createSimpleRecord(Null, nullSchema, createArray(nullSchema, recordPriceSchema))

  //Avro map schemas
  val mapOfStrings: Schema = createMap(nullSchema, stringSchema)

  val recordMapOfStrings: Schema = createSimpleRecord(mapOfStrings)

  val mapOfInts: Schema = createMap(nullSchema, integerSchema)

  val recordMapOfInts: Schema = createRecord(createField(RecordFieldName, mapOfInts))

  val recordMaybeMapOfInts: Schema = createRecord(createField(RecordFieldName, nullSchema, mapOfInts))

  val recordOptionalMapOfInts: Schema = createSimpleRecord(Null, nullSchema, mapOfInts)

  val recordMapOfMapsStrings: Schema = createSimpleRecord(Null, nullSchema, createMap(nullSchema, mapOfStrings))

  val recordOptionalMapOfMapsInts: Schema = createSimpleRecord(Null, nullSchema, createMap(nullSchema, mapOfInts))

  val recordOptionalMapOfStringRecords: Schema = createSimpleRecord(Null, nullSchema, createMap(nullSchema, recordStringPriceSchema))

  val recordOptionalMapOfRecords: Schema = createSimpleRecord(Null, nullSchema, createMap(nullSchema, recordPriceSchema))

  //Avro record schemas
  val nestedRecordWithStringPriceSchema: Schema = createSimpleRecord(Null,
    nullSchema, createRecord(createField("sub", Null,
      nullSchema, recordStringPriceSchema
    ))
  )

  val nestedRecordSchema: Schema = createSimpleRecord(Null,
    nullSchema, createRecord(createField("sub", Null,
      nullSchema, recordPriceSchema
    ))
  )

  val nestedRecordSchemaV2Fields: Schema = Schema.createUnion(
    nullSchema,
    createRecord(
      createField("sub", Null,
        nullSchema,
        createRecord(
          createField("price", nullSchema, doubleSchema),
          createField("currency", "USD", stringSchema),
        )
      ),
      createField("str", stringSchema)
    )
  )

  val nestedRecordSchemaV2: Schema = createSimpleRecord(Null, nestedRecordSchemaV2Fields)

  //Avro other schemas
  val recordStringSchema: Schema = createSimpleRecord(stringSchema)

  val baseEnumSchema: Schema = createEnum("Suit", List("SPADES", "HEARTS", "DIAMONDS", "CLUBS"))

  val recordEnumSchema: Schema = createSimpleRecord(baseEnumSchema)

  val enumSchemaV2: Schema = createEnum("Suit", List("SPADES", "HEARTS2", "DIAMONDS2", "CLUBS2"))

  val recordEnumSchemaV2: Schema = createSimpleRecord(enumSchemaV2)

  val baseFixedSchema: Schema = createFixed("md5", size = 32)

  val recordFixedSchema: Schema = createSimpleRecord(baseFixedSchema)

  val recordFixedSchemaV2: Schema = createSimpleRecord(createFixed("short", size = 16))

  val recordUUIDSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.uuid()))

  val recordDecimalSchema: Schema = createSimpleRecord(AvroUtils.parseSchema("""{"type":"bytes","logicalType":"decimal","precision":4,"scale":2}"""))

  val recordDateSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.date()))

  val recordTimeMillisSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timeMillis()))

  val recordTimeMicrosSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timeMicros()))

  val recordTimestampMillisSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timestampMillis()))

  val recordTimestampMicrosSchema: Schema = createSimpleRecord(createLogical(LogicalTypes.timestampMicros()))

  //Sample data
  val sampleInteger: Int = 1
  val sampleFloat: Float = 13.3.toFloat
  val sampleDouble: Double = 15.5
  val sampleLong: Long = Integer.MAX_VALUE.toLong + 1
  val sampleString: String = "lcl"
  val sampleBoolean: Boolean = true
  val sampleBytes: Array[Byte] = sampleString.getBytes("UTf-8")
  val samplePriceRecord: GenericRecord = AvroUtils.createRecord(recordPriceSchema, Map("price" -> sampleDouble))
  val sampleIntegerArray: util.List[Int] = List(1, 2).asJava
  val sampleArrayInArray: util.List[util.List[Int]] = List(sampleIntegerArray).asJava
  val sampleArrayWithRecord: util.List[GenericRecord] = List(samplePriceRecord).asJava
  val sampleMapInts: util.Map[String, Int] = Map("tax" -> 7).asJava
  val sampleMapOfMapsInts: util.Map[String, util.Map[String, Int]] = Map("first" -> sampleMapInts).asJava
  val sampleMapOfRecords: util.Map[String, GenericRecord] = Map("first" -> samplePriceRecord).asJava

  val typeInt: typing.TypingResult = Typed.fromInstance(sampleInteger)
  val typeLong: typing.TypingResult = Typed.fromInstance(sampleLong)
  val typeFloat: typing.TypingResult = Typed.fromInstance(sampleFloat)
  val typeDouble: typing.TypingResult = Typed.fromInstance(sampleDouble)
  val typeStr: typing.TypingResult = Typed.fromInstance(sampleString)
  val typeBool: typing.TypingResult = Typed.fromInstance(sampleBoolean)

  val sampleNestedRecord: GenericRecord = AvroUtils.createRecord(nestedRecordSchema,
    Map(RecordFieldName -> Map("sub" -> samplePriceRecord))
  )

  val defaultNestedRecordV2: GenericRecord = AvroUtils.createRecord(nestedRecordSchemaV2,
    Map(RecordFieldName -> Map("sub" -> samplePriceRecord, "str" -> "sample"))
  )

  val sampleNestedRecordV2: GenericRecord = AvroUtils.createRecord(nestedRecordSchemaV2,
    Map(RecordFieldName -> Map("sub" -> Map("price" -> sampleDouble, "currency" -> "PLN"), "str" -> "sample"))
  )

  val sampleEnumString = "SPADES"
  val sampleEnum = new EnumSymbol(baseEnumSchema, sampleEnumString)

  val sampleEnumV2String = "HEARTS2"
  val typeEnumV2Str: typing.TypingResult = Typed.fromInstance(sampleEnumV2String)
  val sampleEnumV2 = new EnumSymbol(enumSchemaV2, sampleEnumV2String)

  val sampleFixedString = "098f6bcd4621d373cade4e832627b4f6"
  val sampleFixed = new Fixed(baseFixedSchema, sampleFixedString.getBytes(StandardCharsets.UTF_8))

  val sampleFixedV2String = "7551140914207932"
  val typeFixedV2str: typing.TypingResult = Typed.fromInstance(sampleFixedV2String)
  val sampleFixedV2 = new Fixed(recordFixedSchemaV2, sampleFixedV2String.getBytes(StandardCharsets.UTF_8))

  val sampleUUID: UUID = UUID.randomUUID()
  val sampleDecimal: java.math.BigDecimal = new java.math.BigDecimal(1).setScale(2)
  val sampleDate: LocalDate = LocalDate.now()
  val sampleMillisLocalTime: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MILLIS)
  val sampleMicrosLocalTime: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.MICROS)
  val sampleMillisInstant: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val sampleMicrosInstant: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

  private def createSimpleRecord(schema: Schema*) = createRecord(createField(RecordFieldName, schema:_*))

  private def createSimpleRecord(default: Any, schema: Schema*) = createRecord(createField(RecordFieldName, default, schema:_*))
}
