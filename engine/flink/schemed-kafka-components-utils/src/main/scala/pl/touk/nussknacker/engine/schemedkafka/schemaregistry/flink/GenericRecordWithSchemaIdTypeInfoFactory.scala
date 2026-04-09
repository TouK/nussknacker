package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

import java.lang.reflect.Type
import java.util

class GenericRecordWithSchemaIdTypeInfoFactory extends TypeInfoFactory[GenericRecordWithSchemaId] {

  override def createTypeInfo(
      t: Type,
      genericParameters: util.Map[String, TypeInformation[_]]
  ): TypeInformation[GenericRecordWithSchemaId] = new GenericRecordWithSchemaIdTypeInfo

}
