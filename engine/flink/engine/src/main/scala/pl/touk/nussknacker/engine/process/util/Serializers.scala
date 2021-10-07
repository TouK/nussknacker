package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.flink.api.serialization.{SerializerWithSpecifiedClass, SerializersRegistrar}
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.util.{Failure, Try}

//Watch out, serializers are also serialized. Incompatible SerializationUID on serializer class can lead process state loss (unable to continue from old snapshot).
//This is why we set SerialVersionUID explicit.
//Look:
//org.apache.flink.api.common.typeutils.TypeSerializerSerializationUtil.writeSerializersAndConfigsWithResilience
//org.apache.flink.api.common.typeutils.TypeSerializerSerializationUtil.readSerializersAndConfigsWithResilience
object Serializers extends LazyLogging {

  def registerSerializers(modelData: ModelData, config: ExecutionConfig): Unit = {
    (CaseClassSerializer :: SpelHack :: SpelMapHack :: Nil).map(_.registerIn(config))
    ScalaServiceLoader.load[SerializersRegistrar](getClass.getClassLoader).foreach(_.register(modelData.processConfig, config))
    TimeSerializers.addDefaultSerializers(config)
  }

  @SerialVersionUID(4481573264636646884L)
  //this is not so great, but is OK for now
  object CaseClassSerializer extends SerializerWithSpecifiedClass[Product](false, true) with Serializable {

    override def clazz: Class[_] = classOf[Product]

    override def write(kryo: Kryo, output: Output, obj: Product) = {
      // this method handles case classes with implicit parameters and also inner classes.
      // their constructor takes different parameters than usual case class constructor
      def handleObjWithDifferentParamsCountConstructor(constructorParamsCount: Int) = {
        output.writeInt(constructorParamsCount)
        output.flush()

        // in inner classes definition, '$outer' field is at the end, but in constructor it is the first parameter
        // we look for '$outer` in getFields not getDeclaredFields, cause it can be also parent's field
        val fields = obj.getClass
          .getFields
          .find(_.getName == "$outer")
          .toList ++ obj.getClass.getDeclaredFields

        assume(fields.size >= constructorParamsCount, "To little fields to serialize -> It will be impossible to deserialize this thing anyway")

        fields.take(constructorParamsCount).foreach(field => {
          field.setAccessible(true)
          kryo.writeClassAndObject(output, field.get(obj))
          field.setAccessible(false)
          output.flush()
        })
      }

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
        handleObjWithDifferentParamsCountConstructor(constructorParamsCount.get)
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
        Try({
          val cons = constructors(0)
          val params = (1 to constructorParamsCount).map(_ => kryo.readClassAndObject(input)).toArray[AnyRef]
          cons.newInstance(params: _*).asInstanceOf[Product]
        }).recover {
          case e => logger.error(s"Failed to load obj of class ${obj.getClass.getName}", e); Failure(e)
        }.get
      }
    }

    override def copy(kryo: Kryo, original: Product) = original
  }
}
