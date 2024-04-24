package db.util

import cats.Monad
import slick.dbio
import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.ExecutionContext

object DBIOActionInstances {

  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  val DB = DBIOAction

  // Monadic transformations are complicated when we have to deal with different set of effects, sometimes the effect
  // is "unknown" (e.g. DB.successful returns just Effect)
  def toEffectAll[R](db: DBIOAction[R, NoStream, _]): DB[R] = {
    db.asInstanceOf[DB[R]]
  }

  implicit def dbMonad(implicit ec: ExecutionContext): Monad[DB] = new Monad[DB] {

    override def pure[A](x: A) = dbio.DBIO.successful(x)

    override def flatMap[A, B](fa: DB[A])(f: (A) => DB[B]) = fa.flatMap(f)

    // this is *not* tail recursive
    override def tailRecM[A, B](a: A)(f: (A) => DB[Either[A, B]]): DB[B] =
      f(a).flatMap {
        case Right(r) => pure(r)
        case Left(l)  => tailRecM(l)(f)
      }

  }

}
