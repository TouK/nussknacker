package org.apache.flink.runtime.types

import com.twitter.chill.{Input, KSerializer, Kryo, Output}

import _root_.java.util.{Map => JMap}
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
private class JavaMapWrapperSerializer extends KSerializer[JMap[_, _]] {
  import JavaMapWrapperSerializer._

  override def write(kryo: Kryo, out: Output, obj: JMap[_, _]): Unit =
  // If the object is the wrapper, simply serialize the underlying Scala Map object.
  // Otherwise, serialize the object itself.
    if (obj.getClass == wrapperClass && underlyingMethodOpt.isDefined) {
      kryo.writeClassAndObject(out, underlyingMethodOpt.get.invoke(obj))
    } else {
      kryo.writeClassAndObject(out, obj)
    }

  override def read(kryo: Kryo, in: Input, clz: Class[JMap[_, _]]): JMap[_, _] =
    kryo.readClassAndObject(in) match {
      case scalaMap: Map[_, _] =>
        scalaMap.asJava
      case scalaMap: mutable.Map[_, _] =>
        scalaMap.asJava
      case javaMap: JMap[_, _] =>
        javaMap
    }
}

private object JavaMapWrapperSerializer {
  // The class returned by asJavaMap (scala.collection.convert.Wrappers$MapWrapper).
  val wrapperClass: Class[_ <: JMap[Int, Int]] = mutable.Map.empty[Int, Int].asJava.getClass

  // Get the underlying method so we can use it to get the Scala collection for serialization.
  private val underlyingMethodOpt = {
    try Some(wrapperClass.getDeclaredMethod("underlying"))
    catch {
      case e: Exception =>
        None
    }
  }
}

