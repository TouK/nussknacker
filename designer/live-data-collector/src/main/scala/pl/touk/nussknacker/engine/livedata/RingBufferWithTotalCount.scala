package pl.touk.nussknacker.engine.livedata

import java.util
import java.util.{Map => JMap}
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

private[livedata] class RingBufferWithTotalCount[T](maxSize: Int) {

  private val underlying = new util.LinkedHashMap[Long, T](2 * maxSize, 0.75f, false) {
    override protected def removeEldestEntry(eldest: JMap.Entry[Long, T]): Boolean = size() > maxSize
  }

  private val counter = new AtomicLong(0)

  def values: List[T] = underlying.synchronized {
    underlying.values().asScala.toList
  }

  def totalCount: Long = {
    counter.get()
  }

  def put(value: T): Unit = underlying.synchronized {
    val id = counter.getAndIncrement()
    underlying.put(id, value)
  }

}
