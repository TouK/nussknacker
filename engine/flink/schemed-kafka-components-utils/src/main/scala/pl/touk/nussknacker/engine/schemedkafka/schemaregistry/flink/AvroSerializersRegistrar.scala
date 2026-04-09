package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.java.typeutils.{AvroUtils, TypeExtractor}
import org.apache.flink.configuration.{Configuration, PipelineOptions}
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

import java.util.Collections

class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    logger.debug("Registering Avro serializers")
    val serializerImpl = executionConfig.getSerializerConfig.asInstanceOf[SerializerConfigImpl]
    registerGenericSerializer(serializerImpl)
    registerOptimizedSerializers(serializerImpl)
  }

  /**
   * Registers default serializer that was used before Flink 2.0 - we need this because the new one from
   * Flink's AvroUtils uses a plain GenericDatumWriter that ignores our extended logical type conversions
   */
  private def registerGenericSerializer(serializerConfig: SerializerConfigImpl): Unit = {
    val genericRecordClass = classOf[GenericData.Record]
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(serializerConfig, genericRecordClass)
    if (serializerConfig.getRegisteredTypesWithKryoSerializerClasses.get(genericRecordClass) == null) {
      throw new RuntimeException(
        s"Serializer registration for $genericRecordClass not found, override code needs to be updated"
      )
    }
    serializerConfig.registerTypeWithKryoSerializer(genericRecordClass, classOf[FieldSerializer[_]])
  }

  /**
   * Registers an optimized [[GenericRecordWithSchemaId]] serializer
   */
  private def registerOptimizedSerializers(serializerConfig: SerializerConfigImpl): Unit = {
    // MiniCluster tests load Flink classes reuse the same ClassLoader which means that registration in static TypeExtractor
    // must happen only once, or it will throw an IllegalArgumentException.
    val registeredClass                = classOf[GenericRecordWithSchemaId]
    val registeredTypeInfoFactoryClass = classOf[GenericRecordWithSchemaIdTypeInfoFactory]

    // TODO: test this on real Flink, aren't we leaking classladers via TypeExtractor?
    logger.info(
      s"Registering AvroSerializer\nCL=${this.getClass.getClassLoader}\nCTX=${Thread.currentThread.getContextClassLoader}\nTECL=${classOf[TypeExtractor].getClassLoader}"
    )

    if (Option(TypeExtractor.getTypeInfoFactory(registeredClass))
        .map(_.getClass)
        .contains(registeredTypeInfoFactoryClass)) {
      if (serializerConfig.getRegisteredTypeInfoFactories.containsKey(registeredClass)) {
        // throw en error early, double registration in real environment must never happen
        throw new IllegalStateException("An optimized TypeInfoFactory is already registered")
      }
      serializerConfig.getRegisteredTypeInfoFactories.put(registeredClass, registeredTypeInfoFactoryClass)
      return
    }

    // Reconfigure SerializerConfig instance - this is safe to call, configuration is additive
    val configuration = new Configuration()
    configuration.set(
      PipelineOptions.SERIALIZATION_CONFIG,
      Collections.singletonList(
        // YAML
        s"${classOf[GenericRecordWithSchemaId].getName}: {type: typeinfo, class: ${classOf[GenericRecordWithSchemaIdTypeInfoFactory].getName}}"
      )
    )
    serializerConfig.configure(configuration, Thread.currentThread.getContextClassLoader)
  }

}
