package pl.touk.nussknacker.engine.livedata

import java.util
import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

private[livedata] class RingBufferWithTotalCount[T](maxSize: Int) {

  private val underlying = new util.LinkedHashMap[Long, T](2 * maxSize, 0.75f, false) {
    override protected def removeEldestEntry(eldest: JMap.Entry[Long, T]): Boolean = size() > maxSize
  }

  private var counter: Long = 0

  def values: List[T] = underlying.synchronized {
    underlying.values().asScala.toList
  }

  def totalCount: Long = underlying.synchronized {
    counter
  }

  def put(value: T): Unit = underlying.synchronized {
    counter += 1
    underlying.put(counter, value)
  }

}
