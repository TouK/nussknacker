package pl.touk.nussknacker.ui.db.timeseries.questdb

import scala.collection.mutable

private[questdb] class ThreadAwareObjectPool[T <: AutoCloseable](objectFactory: () => T) {
  private val pool = new mutable.HashMap[Thread, T]()

  def get(): T = {
    val thread = Thread.currentThread()
    pool.getOrElse(
      thread, {
        val t = objectFactory()
        pool.put(thread, t)
        t
      }
    )
  }

  def clear(): Unit = {
    val values = pool.values.toList
    pool.clear()
    values.foreach(_.close())
  }

}
