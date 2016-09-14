package pl.touk.esp.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import shapeless._
import shapeless.ops.hlist.Mapper._
import scala.reflect.ClassTag

object Serializers {

  def registerSerializers(env: StreamExecutionEnvironment): Unit = {

    object registers extends Poly1 {
      implicit def caseSerializer[T,S](implicit ev0: ClassTag[T], ev1: S <:< Serializer[T] with Serializable) = at[S] { s =>
        val klass = implicitly[ClassTag[T]].runtimeClass
        val serializer = ev1(s)
        env.getConfig.getRegisteredTypesWithKryoSerializers.put(klass, new ExecutionConfig.SerializableSerializer(serializer))
        env.getConfig.getDefaultKryoSerializers.put(klass, new ExecutionConfig.SerializableSerializer(serializer))
      }
    }
    (CaseClassSerializer :: SpelHack :: HNil).map(registers)
  }

  //to nadal nie jest jakies super, ale od czegos trzeba zaczac...
  object CaseClassSerializer extends Serializer[Product](false, true) with Serializable {
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
      val cons = obj.getConstructors()(0)
      val arity = input.readInt()
      val params = (1 to arity).map(_ => kryo.readClassAndObject(input)).toArray[AnyRef]
      cons.newInstance(params: _*).asInstanceOf[Product]
    }

    override def copy(kryo: Kryo, original: Product) = original
  }


}
