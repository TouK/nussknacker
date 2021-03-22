package pl.touk.nussknacker.test

import java.util.concurrent.CopyOnWriteArrayList

trait WithDataList[T] extends Serializable {

  private val dataList = new CopyOnWriteArrayList[T]

  def add(element: T) : Unit = dataList.add(element)

  def data : List[T] = {
    dataList.toArray.toList.map(_.asInstanceOf[T])
  }

  def clear() : Unit = {
    dataList.clear()
  }
}
