package pl.touk.nussknacker.test

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

trait WithDataList[T] extends Serializable {

  @transient private val dataList = new CopyOnWriteArrayList[T]

  def add(element: T) : Unit = dataList.add(element)

  def data : List[T] = {
    dataList.toArray.toList.map(_.asInstanceOf[T])
  }

  def clear(predicate: Predicate[T] = _ => true) : Unit = {
    dataList.removeIf(predicate)
  }
}
