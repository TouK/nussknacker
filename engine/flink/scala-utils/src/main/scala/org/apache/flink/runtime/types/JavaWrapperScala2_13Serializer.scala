package org.apache.flink.runtime.types

import com.twitter.chill.{Input, KSerializer, Kryo, Output}

import _root_.java.util.{List => JList, Map => JMap, Set => JSet}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
  * A Kryo serializer for serializing results returned by asJava.
  *
  * The underlying object is scala.collection.convert.Wrappers$MapWrapper.
  * Kryo deserializes this into an AbstractCollection, which unfortunately doesn't work.
  *
  * Ported from Apache Spark's KryoSerializer.scala.
  */
private class JavaWrapperScala2_13Serializer[T](val wrapperClass: Class[_ <: T], val transform: Any => T) extends KSerializer[T] {
  private val underlyingMethodOpt = {
    try Some(wrapperClass.getDeclaredMethod("underlying"))
    catch {
      case _: Exception =>
        None
    }
  }

  override def write(kryo: Kryo, out: Output, obj: T): Unit =
  // If the object is the wrapper, simply serialize the underlying Scala object.
  // Otherwise, serialize the object itself.
    if (obj.getClass == wrapperClass && underlyingMethodOpt.isDefined) {
      kryo.writeClassAndObject(out, underlyingMethodOpt.get.invoke(obj))
    } else {
      kryo.writeClassAndObject(out, obj)
    }

  override def read(kryo: Kryo, in: Input, clz: Class[T]): T =
    transform(kryo.readClassAndObject(in))
}

private object JavaWrapperScala2_13Serializers {
  // The class returned by asJava (scala.collection.convert.Wrappers$MapWrapper).
  private val mapWrapperClass: Class[_ <: JMap[Int, Int]] = mutable.Map.empty[Int, Int].asJava.getClass
  val mapSerializer = new JavaWrapperScala2_13Serializer[JMap[_, _]](mapWrapperClass, {
    case scalaMap: Map[_, _] =>
      scalaMap.asJava
    case scalaMap: mutable.Map[_, _] =>
      scalaMap.asJava
    case javaMap: JMap[_, _] =>
      javaMap
  })

  private val listWrapperClass: Class[_ <: JList[Int]] = mutable.Buffer.empty[Int].asJava.getClass
  val listSerializer = new JavaWrapperScala2_13Serializer[JList[_]](listWrapperClass, {
    case scalaList: List[_] =>
      scalaList.asJava
    case scalaList: mutable.Buffer[_] =>
      scalaList.asJava
    case javaList: JList[_] =>
      javaList
  })

  private val setWrapperClass: Class[_ <: JSet[Int]] = mutable.Set.empty[Int].asJava.getClass
  val setSerializer = new JavaWrapperScala2_13Serializer[JSet[_]](setWrapperClass, {
    case scalaSet: Set[_] =>
      scalaSet.asJava
    case scalaSet: mutable.Set[_] =>
      scalaSet.asJava
    case javaSet: JSet[_] =>
      javaSet
  })
}

