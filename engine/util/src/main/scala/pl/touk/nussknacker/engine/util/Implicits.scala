package pl.touk.nussknacker.engine.util

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}

object Implicits {

  implicit class RichScalaMap[K <: Any, V <: Any](m: Map[K, V]) {

    def mapValuesNow[VV](f: V => VV): Map[K, VV] = m.map { case (k, v) => k -> f(v) }
  }

  implicit class RichTupleList[K, V](seq: List[(K, V)]) {

    def toGroupedMap: Map[K, List[V]] =
      seq.groupBy(_._1).mapValuesNow(_.map(_._2))

  }

  implicit class RichMapIterable[K,V](m: Map[K, Iterable[V]]) {
    def sequenceMap: Map[V, Iterable[K]] = {
      m.map { case (k, values) =>
        values.map(v => v -> k)
      }.toList.flatten.groupBy(_._1).mapValues(_.map(_._2))
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
        case Failure(_) => // ignoring - side effect should be applied only when success
      }
      future
    }
  }

  implicit class SafeString(s: String) {
    def safeValue: Option[String] = {
      if (s == null || s == "") None else Some(s)
    }
  }

  implicit class RichIterableMap[T](list: Iterable[Map[String, T]]) {
    def reduceUnique: Map[String, T] = list.foldLeft(Map.empty[String, T]) {
      case (acc, element) =>
        val duplicates = acc.keySet.intersect(element.keySet)
        if (duplicates.isEmpty) {
          acc ++ element
        } else throw new IllegalArgumentException(s"Found duplicate keys: ${duplicates.mkString(", ")}, please correct configuration")
    }
  }

}
