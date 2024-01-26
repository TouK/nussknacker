package pl.touk.nussknacker.engine.util

import cats.data.NonEmptyList

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}

object Implicits {

  implicit class RichScalaMap[K <: Any, V <: Any](m: Map[K, V]) {

    def mapValuesNow[VV](f: V => VV): Map[K, VV] = m.map { case (k, v) => k -> f(v) }

    def filterKeysNow(f: K => Boolean): Map[K, V] = m.filter { case (k, _) => f(k) }
  }

  implicit class RichTupleList[K, V](seq: List[(K, V)]) {

    def toGroupedMap: Map[K, List[V]] =
      seq.groupBy(_._1).mapValuesNow(_.map(_._2))

    def toGroupedMapSafe: Map[K, NonEmptyList[V]] =
      seq.groupBy(_._1).mapValuesNow(groupedValue => NonEmptyList.fromListUnsafe(groupedValue.map(_._2)))

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

  implicit class RichIterable[T](iterable: Iterable[T]) {

    def exactlyOne: Option[T] = iterable match {
      case head :: Nil => Some(head)
      case _           => None
    }

  }

}
