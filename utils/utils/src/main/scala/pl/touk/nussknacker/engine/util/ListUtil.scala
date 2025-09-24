package pl.touk.nussknacker.engine.util

import scala.annotation.tailrec

object ListUtil {

  def mergeListsRoundRobin[T](lists: List[List[T]], size: Int): List[T] = {
    mergeListsRoundRobin(lists).take(size)
  }

  @tailrec
  private def mergeListsRoundRobin[T](lists: List[List[T]], acc: List[T] = List()): List[T] = lists match {
    case Nil                         => acc
    case Nil :: next                 => mergeListsRoundRobin(next, acc)
    case (head :: rest) :: nextLists => mergeListsRoundRobin(nextLists :+ rest, acc :+ head)
  }

}
