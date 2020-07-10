package pl.touk.nussknacker.engine.avro

import org.apache.avro.Conversions.{DecimalConversion, UUIDConversion}
import org.apache.avro.Schema
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.GenericData
import org.apache.avro.reflect.ReflectData
import org.apache.avro.specific.SpecificData

object AvroUtils {

  def genericData: GenericData = addLogicalTypeConversions(new GenericData(_))

  def specificData: SpecificData = addLogicalTypeConversions(new SpecificData(_))

  def reflectData: ReflectData = addLogicalTypeConversions(new ReflectData(_))

  private def addLogicalTypeConversions[T <: GenericData](createData: ClassLoader => T): T = {
    val data = createData(Thread.currentThread.getContextClassLoader)
    data.addLogicalTypeConversion(new TimeConversions.DateConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimeMillisConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion)
    data.addLogicalTypeConversion(new UUIDConversion)
    data.addLogicalTypeConversion(new DecimalConversion)
    data
  }

  private def parser = new Schema.Parser()

  def parseSchema(avroSchema: String): Schema =
    parser.parse(avroSchema)

}
