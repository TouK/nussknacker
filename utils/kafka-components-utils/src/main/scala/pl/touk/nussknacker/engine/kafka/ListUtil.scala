package pl.touk.nussknacker.engine.kafka

import scala.annotation.tailrec

private[engine] object ListUtil {

  //TODO: is sorting ok here?
  def mergeListsFromTopics[T](lists: List[List[T]], size: Int): List[T] = {
    mergeLists(lists).take(size)
  }


  @tailrec
  private def mergeLists[T](lists: List[List[T]], acc: List[T] = List()): List[T] = lists match {
    case Nil => acc
    case Nil :: next => mergeLists(next, acc)
    case (head :: rest) :: nextLists => mergeLists(nextLists :+ rest, acc :+ head)
  }

}
