package pl.touk.nussknacker.engine.livedata

import java.util
import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

private[livedata] class RingBuffer[K, V](maxSize: Int) {

  private val underlying = new util.LinkedHashMap[K, V](2 * maxSize, 0.75f, false) {
    override protected def removeEldestEntry(eldest: JMap.Entry[K, V]): Boolean = size() > maxSize
  }

  def values: List[V] = underlying.synchronized {
    underlying.values().asScala.toList
  }

  def update(key: K, f: Option[V] => V): V = underlying.synchronized {
    underlying.compute(key, (_: K, output: V) => f(Option(output)))
  }

  def clear(): Unit = underlying.synchronized {
    underlying.clear()
  }

}
