package pl.touk.nussknacker.engine.avro.schema

import io.circe.{Encoder, Json}
import org.apache.avro.{LogicalTypes, Schema}
import org.apache.avro.data.TimeConversions
import org.apache.avro.specific.SpecificRecordBase
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.time._

// To avoid name collision with GeneratedAvroClassWithLogicalTypes
object GeneratedAvroClassWithLogicalTypesSchema extends TestSchemaWithSpecificRecord {

  val date: LocalDateTime = LocalDateTime.of(2020, 1, 2, 3, 14, 15)

  val instant: Instant = date.toInstant(ZoneOffset.UTC)

  val decimal: java.math.BigDecimal = null // TODO: fixedEncoder does not handle avro decimalConversion

  override lazy val schema: Schema = GeneratedAvroClassWithLogicalTypes.SCHEMA$

  override def specificRecord: SpecificRecordBase = new GeneratedAvroClassWithLogicalTypes(
    exampleData("text").asInstanceOf[CharSequence],
    exampleData("dateTime").asInstanceOf[Instant],
    exampleData("date").asInstanceOf[LocalDate],
    exampleData("time").asInstanceOf[LocalTime],
    exampleData("decimal").asInstanceOf[java.math.BigDecimal]
  )

  override def exampleData: Map[String, Any] = Map(
    "text" -> "lorem ipsum",
    "dateTime" -> instant,
    "date" -> date.toLocalDate,
    "time" -> date.toLocalTime,
    "decimal" -> decimal
  )

  override def stringSchema: String = "" // see hardcoded definition in GeneratedAvroClassWithLogicalTypes

  val fixedEncoder: Encoder[GeneratedAvroClassWithLogicalTypes] = new Encoder[GeneratedAvroClassWithLogicalTypes] {
    override def apply(a: GeneratedAvroClassWithLogicalTypes): Json = {
      val map = Map(
        "text" -> a.getText,
        "dateTime" -> Option(a.getDateTime).map(value => timestampConversion.toLong(value, null, null)),
        "date" -> Option(a.getDate).map(value => dateConversion.toInt(value, null, null)),
        "time" -> Option(a.getTime).map(value => timeMillisConversion.toInt(value, null, null)),
        "decimal" -> Option(a.getDecimal).map(value => new String(decimalConversion.toBytes(value, null, LogicalTypes.decimal(4, 2)).array()))
      )
      encoder.encode(map)
    }
  }

  private val encoder = BestEffortJsonEncoder(failOnUnkown = true)

  // avro conversions
  // TODO: When confluent json schema is used instead of avro schema for json payloads, remove these avro conversions.
  private lazy val dateConversion = new TimeConversions.DateConversion
  private lazy val timestampConversion = new TimeConversions.TimestampMillisConversion
  private lazy val decimalConversion = new org.apache.avro.Conversions.DecimalConversion
  private lazy val timeMillisConversion = new TimeConversions.TimeMillisConversion

}
