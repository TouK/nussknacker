package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema

object AvroUtils {

  private def parser = new Schema.Parser()

  def parseSchema(avroSchema: String): Schema =
    parser.parse(avroSchema)
}
