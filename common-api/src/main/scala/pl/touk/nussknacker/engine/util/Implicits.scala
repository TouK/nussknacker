package pl.touk.nussknacker.engine.util

import cats.data.NonEmptyList

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}

object Implicits {

  implicit class RichScalaMap[K <: Any, V <: Any](m: Map[K, V]) {

    def mapValuesNow[VV](f: V => VV): Map[K, VV] = m.map { case (k, v) => k -> f(v) }

    def filterKeysNow(f: K => Boolean): Map[K, V] = m.filter { case (k, _) => f(k) }
  }

  implicit class RichTupleList[K, V](seq: List[(K, V)]) {

    def toGroupedMap: ListMap[K, List[V]] =
      ListMap(
        seq.orderedGroupBy(_._1).map { case (k, vList) =>
          k -> vList.map(_._2)
        }: _*
      )

    def toGroupedMapSafe: ListMap[K, NonEmptyList[V]] =
      toGroupedMap.map { case (k, v) => k -> NonEmptyList.fromListUnsafe(v) }

    def toMapCheckingDuplicates: Map[K, V] = {
      val moreThanOneValueForKey = seq.toGroupedMap.filter(_._2.size > 1)
      if (moreThanOneValueForKey.nonEmpty)
        throw new IllegalStateException(
          s"Found keys with more than one values: ${moreThanOneValueForKey.keys.mkString(", ")} during translating $seq to Map"
        )
      seq.toMap
    }

  }

  implicit class RichStringList(seq: List[String]) {

    def mkCommaSeparatedStringWithPotentialEllipsis(maxEntries: Int): String = {
      if (seq.size <= maxEntries)
        seq.mkString(", ")
      else
        seq.take(maxEntries).mkString("", ", ", ", ...")
    }

  }

  implicit class RichFuture[A](future: Future[A]) {

    def withSideEffect(f: A => Unit)(implicit ec: ExecutionContext): Future[A] = {
      future.onComplete {
        case Success(result) => f(result)
        case Failure(_)      => // ignoring - side effect should be applied only when success
      }
      future
    }

  }

  implicit class RichIterable[T](list: List[T]) {

    def exactlyOne: Option[T] = list match {
      case head :: Nil => Some(head)
      case _           => None
    }

    def orderedGroupBy[P](f: T => P): List[(P, List[T])] = {
      @tailrec
      def accumulator(seq: List[T], f: T => P, res: List[(P, List[T])]): List[(P, List[T])] = seq.headOption match {
        case None => res.reverse
        case Some(h) =>
          val key                         = f(h)
          val (withSameKey, withOtherKey) = seq.partition(f(_) == key)
          accumulator(withOtherKey, f, (key -> withSameKey) :: res)
      }

      accumulator(list, f, Nil)
    }

  }

}
