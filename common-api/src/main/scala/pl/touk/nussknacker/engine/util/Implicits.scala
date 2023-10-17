package pl.touk.nussknacker.engine.util

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Using.Releasable
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

  }

  implicit class RichList[T](list: List[T]) {

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

  implicit class RichStringList(seq: List[String]) {

    def sortCaseInsensitive: List[String] = {
      seq.sortBy(_.toLowerCase)
    }

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

  implicit object SourceIsReleasable extends Releasable[scala.io.Source] {
    def release(resource: scala.io.Source): Unit = resource.close()
  }

  implicit class RichIterable[T](iterable: Iterable[T]) {

    def exactlyOne: Option[T] = iterable match {
      case head :: Nil => Some(head)
      case _           => None
    }

  }

}
