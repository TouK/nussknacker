package db.util

import cats.Monad
import slick.dbio
import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.ExecutionContext

object DBIOActionInstances {

  // In order to provide Monad type class we have to align all actions to the same Effect. Because of that
  // we use the bottom type which is Effect.All.
  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  val DB = DBIOAction

  def toEffectAll[R](db: DBIOAction[R, NoStream, _]): DB[R] = {
    // Effect is a phantom type so casting should be safe
    db.asInstanceOf[DB[R]]
  }

  implicit def dbMonad(implicit ec: ExecutionContext): Monad[DB] = new Monad[DB] {

    override def pure[A](x: A): DB[A] = dbio.DBIO.successful(x)

    override def flatMap[A, B](fa: DB[A])(f: A => DB[B]): DB[B] = fa.flatMap(f)

    // this is *not* tail recursive
    override def tailRecM[A, B](a: A)(f: A => DB[Either[A, B]]): DB[B] =
      f(a).flatMap {
        case Right(r) => pure(r)
        case Left(l)  => tailRecM(l)(f)
      }

  }

}
