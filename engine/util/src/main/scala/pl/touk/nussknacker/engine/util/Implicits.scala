package pl.touk.nussknacker.engine.util

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object Implicits {

  implicit class RichScalaMap[K <: Any, V <: Any](m: Map[K, V]) {

    def mapValuesNow[VV](f: V => VV): Map[K, VV] = m.map { case (k, v) => k -> f(v) }
  }

  implicit class RichTupleList[K, V](seq: List[(K, V)]) {

    def toGroupedMap: Map[K, List[V]] =
      seq.groupBy(_._1).mapValuesNow(_.map(_._2))

  }

  implicit class RichString(s: String) {
    def safeToOption: Option[String] = {
      if (s == null || s == "") None
      else Some(s)
    }
  }

  implicit class RichFuture[A](future: Future[A]) {
    def effect(f: () => Unit)(implicit ec: ExecutionContext): Future[A] = for {
      result <- future
      _      = f()
    } yield result
  }
}
