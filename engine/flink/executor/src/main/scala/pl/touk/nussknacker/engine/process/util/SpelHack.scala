package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.{Kryo, Serializer}
import com.esotericsoftware.kryo.io.{Input, Output}
import pl.touk.nussknacker.engine.flink.api.serialization.{ClassBasedKryoSerializerRegistrar, SerializerRegistrar}

import java.util

//By default SpEL list (i.e "{'a', 'b'}") is represented as java.util.Collections.UnmodifiableCollection, which
//Kryo won't serialize properly since Kry uses java.util.Collection.add() method which in case of UnmodifiableCollection
//throws an exception. That's why we need out own serialization of java.util.List
//The same applies to Map
class SpelHack extends Serializer[java.util.List[_]](false, true) {

  override def write(kryo: Kryo, out: Output, obj: java.util.List[_]): Unit = {
    // Write the size:
    out.writeInt(obj.size(), true)
    val it = obj.iterator()
    while (it.hasNext) {
      kryo.writeClassAndObject(out, it.next())
    }

  }

  override def read(kryo: Kryo, in: Input, obj: Class[java.util.List[_]]): java.util.List[_] = {
    val size = in.readInt(true)
    // Go ahead and be faster, and not as functional cool, and be mutable in here
    var idx  = 0
    val list = new java.util.ArrayList[AnyRef](size)
    while (idx < size) {
      val item = kryo.readClassAndObject(in)
      list.add(item)
      idx += 1
    }
    list
  }

}

object SpelHack {

  implicit val registrar: SerializerRegistrar[SpelHack] = {
    // It is loaded by loadClass because UnmodifiableCollection has private-package access
    val unmodifiableCollectionClass = getClass.getClassLoader
      .loadClass("java.util.Collections$UnmodifiableCollection")
      .asInstanceOf[Class[java.util.List[_]]]
    new ClassBasedKryoSerializerRegistrar(classOf[SpelHack], unmodifiableCollectionClass)
  }

}

class SpelMapHack extends Serializer[java.util.Map[_, _]](false, true) {

  override def write(kryo: Kryo, out: Output, obj: java.util.Map[_, _]): Unit = {
    // Write the size:
    out.writeInt(obj.size(), true)
    val it = obj.entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      kryo.writeClassAndObject(out, entry.getKey)
      kryo.writeClassAndObject(out, entry.getValue)
    }
  }

  override def read(kryo: Kryo, in: Input, obj: Class[java.util.Map[_, _]]): java.util.Map[_, _] = {
    val size = in.readInt(true)
    // Go ahead and be faster, and not as functional cool, and be mutable in here
    var idx = 0
    val map = new util.HashMap[AnyRef, AnyRef](size)

    while (idx < size) {
      val key   = kryo.readClassAndObject(in)
      val value = kryo.readClassAndObject(in)

      map.put(key, value)
      idx += 1
    }
    map
  }

}

object SpelMapHack {

  implicit val registrar: SerializerRegistrar[SpelMapHack] = {
    // It is loaded by loadClass because UnmodifiableCollection has private-package access
    val unmodifiableMapClass = getClass.getClassLoader
      .loadClass("java.util.Collections$UnmodifiableMap")
      .asInstanceOf[Class[java.util.Map[_, _]]]
    new ClassBasedKryoSerializerRegistrar(classOf[SpelMapHack], unmodifiableMapClass)
  }

}
