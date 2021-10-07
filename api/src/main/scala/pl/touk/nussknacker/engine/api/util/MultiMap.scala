package pl.touk.nussknacker.engine.api.util

import scala.collection.immutable.TreeMap

object MultiMap {
  def apply[K:Ordering, V] : MultiMap[K, V] = MultiMap(TreeMap())
}

case class MultiMap[K, V](map: TreeMap[K, List[V]]) {

  def add(key: K, value: V) : MultiMap[K, V] = {
    val newElement = map.get(key) match {
      case Some(list) => value::list
      case None => List(value)
    }
    MultiMap(map + (key -> newElement))
  }

  def add(key: K, values: List[V]) : MultiMap[K, V] = {
    val newElement = map.get(key) match {
      case Some(list) => values:::list
      case None => values
    }
    MultiMap(map + (key -> newElement))
  }

  def remove(key: K, value: V) : MultiMap[K, V] = {
    map.get(key) match {
      case Some(list) =>
        //TODO: this is only ineffective operation, but in our case lists should be rather short
        val withRemovedEl = list.filterNot(_ == value)
        MultiMap(map + (key -> withRemovedEl))
      case None =>
        this
    }

  }

  def from(minimalKey: K) = MultiMap(map.from(minimalKey))

  def until(minimalKey: K) = MultiMap(map.until(minimalKey))


}