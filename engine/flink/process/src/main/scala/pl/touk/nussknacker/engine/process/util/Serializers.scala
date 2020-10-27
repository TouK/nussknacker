package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.util.ThreadUtils

import scala.util.{Failure, Try}

//Watch out, serializers are also serialized. Incompatible SerializationUID on serializer class can lead process state loss (unable to continue from old snapshot).
//This is why we set SerialVersionUID explicit.
//Look:
//org.apache.flink.api.common.typeutils.TypeSerializerSerializationUtil.writeSerializersAndConfigsWithResilience
//org.apache.flink.api.common.typeutils.TypeSerializerSerializationUtil.readSerializersAndConfigsWithResilience
object Serializers extends LazyLogging {

  def registerSerializers(config: ExecutionConfig): Unit = {
    val registers = registerSerializer(config) _
    (CaseClassSerializer ::  SpelHack :: SpelMapHack :: Nil).map(registers)

    TimeSerializers.addDefaultSerializers(config)

    addAvroSerializersIfRequired(config)
  }

  private def addAvroSerializersIfRequired(config: ExecutionConfig): Unit = {
    // We need it because we use avro records inside our Context class
    Try(ThreadUtils.loadUsingContextLoader("org.apache.avro.generic.GenericData$Record")).map { genericRecordClass =>
      logger.debug("Registering avro serializers")
      AvroUtils.getAvroUtils.addAvroSerializersIfRequired(config, genericRecordClass)
    }.getOrElse {
      logger.debug("Can't find avro in class path - skipping registration of serializers")
    }
  }

  private def registerSerializer(config: ExecutionConfig)(serializer: SerializerWithSpecifiedClass[_]) = {
    config.getRegisteredTypesWithKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
    config.getDefaultKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
  }

  abstract class SerializerWithSpecifiedClass[T](acceptsNull: Boolean, immutable: Boolean)
    extends Serializer[T](acceptsNull, immutable) with Serializable {

    def clazz: Class[_]

  }

  @SerialVersionUID(4481573264636646884L)
  //this is not so great, but is OK for now
  object CaseClassSerializer extends SerializerWithSpecifiedClass[Product](false, true) with Serializable {

    override def clazz: Class[_] = classOf[Product]

    override def write(kryo: Kryo, output: Output, obj: Product) = {
      val arity = obj.productArity
      val constructorParamsCount = obj.getClass.getConstructors.headOption.map(_.getParameterCount)

      if (arity == constructorParamsCount.getOrElse(0)) {
        output.writeInt(arity)
        output.flush()
        obj.productIterator.foreach { f =>
          kryo.writeClassAndObject(output, f)
          output.flush()
        }
      } else {
        output.writeInt(constructorParamsCount.get)
        output.flush()

        // in inner classes definition, '$outer' field is at the end, but in constructor it is the first parameter
        val fields = obj.getClass
          .getDeclaredFields
          .find(_.getName == "$outer")
          .toList ++ obj.getClass.getDeclaredFields

        fields.take(constructorParamsCount.get).foreach(field => {
          field.setAccessible(true)
          kryo.writeClassAndObject(output, field.get(obj))
          field.setAccessible(false)
          output.flush()
        })
      }
      output.flush()
    }

    override def read(kryo: Kryo, input: Input, obj: Class[Product]) = {
      val constructorParamsCount = input.readInt()
      val constructors = obj.getConstructors

      if (constructorParamsCount == 0 && constructors.isEmpty) {
        Try(EspTypeUtils.companionObject(obj)).recover {
          case e => logger.error(s"Failed to load companion for ${obj.getClass}"); Failure(e)
        }.get
      } else {
        val cons = constructors(0)
        val params = (1 to constructorParamsCount).map(_ => kryo.readClassAndObject(input)).toArray[AnyRef]
        Try(cons.newInstance(params: _*).asInstanceOf[Product]).recover {
          case e => logger.error(s"Failed to load obj of class ${obj.getClass.getName}", e); Failure(e)
        }.get
      }
    }

    override def copy(kryo: Kryo, original: Product) = original
  }
}
