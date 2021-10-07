package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

trait BaseJavaMapBasedSerializer[V>:AnyRef, T<:java.util.Map[String, V]] { self: TypedObjectBasedTypeSerializer[T] =>

  override def deserialize(values: Array[AnyRef]): T = {
    val size = serializers.length
    val map = createInstance()
    (0 until size).foreach { idx =>
      map.put(name(idx), values(idx))
    }
    map
  }

  override def get(value: T, k: String): AnyRef = value.get(k).asInstanceOf[AnyRef]

}
