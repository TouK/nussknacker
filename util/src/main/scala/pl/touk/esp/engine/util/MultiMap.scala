package pl.touk.esp.engine.util

import scala.collection.immutable.TreeMap

object MultiMap {
  def apply[K:Ordering, V] : MultiMap[K, V] = MultiMap(TreeMap())
}

case class MultiMap[K:Ordering, V](map: TreeMap[K, List[V]]) {

  def add(key: K, value: V) : MultiMap[K, V] = {
    val newElement = map.get(key) match {
      case Some(list) => value::list
      case None => List(value)
    }
    MultiMap(map + (key -> newElement))
  }

  def remove(key: K, value: V) : MultiMap[K, V] = {
    map.get(key) match {
      case Some(list) =>
        //TODO: to jest jedyna nieefektywna operacja, ale w naszych przypadkach listy powinny byc dosc krotkie ;)
        val withRemovedEl = list.filterNot(_ == value)
        MultiMap(map + (key -> withRemovedEl))
      case None =>
        this
    }

  }

  def from(minimalKey: K) = MultiMap(map.from(minimalKey))

  def until(minimalKey: K) = MultiMap(map.until(minimalKey))


}

