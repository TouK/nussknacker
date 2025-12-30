package org.apache.flink.runtime.types

import com.esotericsoftware.kryo.Kryo
import com.twitter.chill.java.{
  UnmodifiableCollectionSerializer,
  UnmodifiableListSerializer,
  UnmodifiableMapSerializer,
  UnmodifiableSetSerializer,
  UnmodifiableSortedMapSerializer,
  UnmodifiableSortedSetSerializer
}
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.flink.api.java.typeutils.runtime.kryo.FlinkChillPackageRegistrar
import org.apache.flink.api.java.typeutils.runtime.kryo.Serializers.SpecificInstanceCollectionSerializerForArrayList
import org.apache.flink.formats.avro.utils.AvroKryoSerializerUtils.AvroSchemaSerializer
import org.objenesis.strategy.StdInstantiatorStrategy

class FlinkScalaKryoInstantiator extends LazyLogging {

  def newKryo: Kryo = {
    val k = new Kryo
    k.setRegistrationRequired(false)
    val initStrategy = new Kryo.DefaultInstantiatorStrategy
    initStrategy.setFallbackInstantiatorStrategy(new StdInstantiatorStrategy)
    k.setInstantiatorStrategy(initStrategy)

    // Handle cases where we may have an odd classloader setup like with libjars
    // for hadoop
    val classLoader = Thread.currentThread.getContextClassLoader
    k.setClassLoader(classLoader)

    addDefaultSerializersForAvro(k)
    registerUnmodifiableJavaCollectionsSerializers(k)
    new FlinkChillPackageRegistrar().registerSerializers(k)
    k
  }

  // See AvroKryoSerializerUtils.addAvroSerializersIfRequired
  private def addDefaultSerializersForAvro(kryo: Kryo): Unit = {
    logger.debug("Adding default serializers for AVRO classes")
    kryo.addDefaultSerializer(classOf[GenericData.Array[_]], new SpecificInstanceCollectionSerializerForArrayList)
    kryo.addDefaultSerializer(classOf[Schema], new AvroSchemaSerializer)
  }

  private def registerUnmodifiableJavaCollectionsSerializers(k: Kryo): Unit = {
    UnmodifiableCollectionSerializer.registrar.apply(k)
    UnmodifiableListSerializer.registrar.apply(k)
    UnmodifiableMapSerializer.registrar.apply(k)
    UnmodifiableSetSerializer.registrar.apply(k)
    UnmodifiableSortedMapSerializer.registrar.apply(k)
    UnmodifiableSortedSetSerializer.registrar.apply(k)
  }

}
