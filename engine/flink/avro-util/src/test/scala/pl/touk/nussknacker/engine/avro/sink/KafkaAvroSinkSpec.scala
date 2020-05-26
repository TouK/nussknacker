package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor

trait KafkaAvroSinkSpec {

  protected def createOutput(schema: Schema, data: Map[String, Any]): LazyParameter[GenericContainer] = {
    val record = BestEffortAvroEncoder.encodeRecordOrError(data, schema)
    new LazyParameter[GenericContainer] {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(record.getSchema)
    }
  }
}
