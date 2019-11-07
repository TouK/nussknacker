package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.types.EspTypeUtils

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
      output.writeInt(obj.productArity)
      output.flush()
      obj.productIterator.foreach { f =>
        kryo.writeClassAndObject(output, f)
        output.flush()
      }
      output.flush()
    }

    override def read(kryo: Kryo, input: Input, obj: Class[Product]) = {
      val arity = input.readInt()
      val constructors = obj.getConstructors

      //TODO: what about case class without parameters??
      if (arity == 0 && constructors.isEmpty) {
        Try(EspTypeUtils.getCompanionObject(obj)).recover {
          case e => logger.error(s"Failed to load companion for ${obj.getClass}"); Failure(e)
        }.get
      } else {
        val cons = constructors(0)
        val params = (1 to arity).map(_ => kryo.readClassAndObject(input)).toArray[AnyRef]
        cons.newInstance(params: _*).asInstanceOf[Product]
      }
    }

    override def copy(kryo: Kryo, original: Product) = original
  }


}
