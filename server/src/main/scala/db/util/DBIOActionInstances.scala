package db.util

import cats.Monad
import slick.dbio
import slick.dbio.{DBIOAction, NoStream, Effect}

import scala.concurrent.ExecutionContext

object DBIOActionInstances {

  type DB[A] = DBIOAction[A, NoStream, Effect.All]

  implicit def dbMonad(implicit ec: ExecutionContext): Monad[DB] = new Monad[DB] {

    override def pure[A](x: A) = dbio.DBIO.successful(x)

    override def flatMap[A, B](fa: DB[A])(f: (A) => DB[B]) = fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: (A) => DB[Either[A, B]]) = defaultTailRecM(a)(f)
  }


}
