package pl.touk.nussknacker.engine.avro.typed

/**
  * @param skipOptionalFields Right now we're doing approximate type generation to avoid false positives in validation,
  *                           so now we add option to skip nullable fields.
  *                           @TODO In future should do it in another way
  * @param useStringForStringSchema if set to false Avro string schema type will be represented internally
  *                                 by {@link org.apache.avro.util.Utf8} to reduce memory overhead
  *                                 Otherwise String class will be used which internally uses UTF16 before jdk9
  *                                 (in jdk>=9 we have Compact Strings which uses byte[] instead of char[]
  *                                 and data is coded in LATIN1 when possible)
  */
case class AvroSettings(useStringForStringSchema: Boolean, skipOptionalFields: Boolean)

object AvroSettings {
  val default: AvroSettings = AvroSettings(useStringForStringSchema = false, skipOptionalFields = false)
}
