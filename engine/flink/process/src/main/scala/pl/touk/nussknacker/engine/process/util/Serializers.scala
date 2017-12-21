package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.util.{Failure, Try}

object Serializers extends LazyLogging {

  def registerSerializers(env: StreamExecutionEnvironment): Unit = {
    val registers = registerSerializer(env) _
    (CaseClassSerializer ::  SpelHack :: Nil).map(registers)

    TimeSerializers.addDefaultSerializers(env)

  }

  private def registerSerializer(env: StreamExecutionEnvironment)(serializer: SerializerWithSpecifiedClass[_]) = {
    env.getConfig.getRegisteredTypesWithKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
    env.getConfig.getDefaultKryoSerializers.put(serializer.clazz, new ExecutionConfig.SerializableSerializer(serializer))
  }

  abstract class SerializerWithSpecifiedClass[T](acceptsNull: Boolean, immutable: Boolean)
    extends Serializer[T](acceptsNull, immutable) with Serializable {

    def clazz: Class[_]

  }

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
