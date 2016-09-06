package pl.touk.esp.engine.optics

import monocle._
import monocle.function.Plated
import monocle.function.Plated._
import pl.touk.esp.engine.canonicalgraph.canonicalnode.{CanonicalNode, Case, Filter, Switch}
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, CanonicalTreeNode}

import scala.language.{higherKinds, implicitConversions}
import scala.reflect.ClassTag
import scalaz.std.list._
import scalaz.syntax.all._
import scalaz.{Applicative, Monad}
import ProcessOptics._

class ProcessOptics(process: CanonicalProcess) {

  private implicit val plated = new Plated[CanonicalTreeNode] {
    override def plate: PTraversal[CanonicalTreeNode, CanonicalTreeNode, CanonicalTreeNode, CanonicalTreeNode] =
      new PTraversal[CanonicalTreeNode, CanonicalTreeNode, CanonicalTreeNode, CanonicalTreeNode] {
        def modifyF[F[_] : Applicative](f: CanonicalTreeNode => F[CanonicalTreeNode])
                                       (s: CanonicalTreeNode): F[CanonicalTreeNode] =
          s match {
            case n: CanonicalProcess =>
              n.nodes.traverse(f).map { cn =>
                n.copy(nodes = cn.asInstanceOf[List[CanonicalNode]])
              }
            case n: Filter =>
              n.nextFalse.traverse(f).map { cn =>
                n.copy(nextFalse = cn.asInstanceOf[List[CanonicalNode]])
              }
            case n: Switch =>
              (n.nexts.traverse(f) |@| n.defaultNext.traverse(f)) { (cn, df) =>
                n.copy(nexts = cn.asInstanceOf[List[Case]], defaultNext = df.asInstanceOf[List[CanonicalNode]])
              }
            case n: Case =>
              n.nodes.traverse(f).map { cn =>
                n.copy(nodes = cn.asInstanceOf[List[CanonicalNode]])
              }
            case _ =>
              Applicative[F].point(s)
          }
      }
  }

  def select[T <: CanonicalNode: ClassTag](nodeId: String): Option[T] = {
    universe(process.asInstanceOf[CanonicalTreeNode]).collectFirst {
      case e: T if e.asInstanceOf[T].id == nodeId => e
    }
  }

  def modify[T <: CanonicalNode: ClassTag](nodeId: String)(f: T => T): ModifyResult[CanonicalProcess] = {
    transform[CanonicalTreeNode, ModifyResult] {
      case e:T if e.id == nodeId =>
        ModifyResult(f(e), modifiedCount = 1)
      case other =>
        ModifyResult(other, modifiedCount = 0)
    }(process).asInstanceOf[ModifyResult[CanonicalProcess]]
  }

  private def transform[A: Plated, M[_]: Monad](f: A => M[A])(a: A): M[A] = {
    val l = plate[A]
    def go(c: A): M[A] =
      l.modifyF[M](b => f(b).flatMap(go))(c)
    go(a)
  }

}

object ProcessOptics {

  case class ModifyResult[V](value: V, modifiedCount: Int) {

    def flatMap[NV](f: V => ModifyResult[NV]): ModifyResult[NV] = {
      val fv = f(value)
      ModifyResult(fv.value, modifiedCount + fv.modifiedCount)
    }

  }

  implicit val modifyResultMonad: Monad[ModifyResult] = new Monad[ModifyResult] {

    override def bind[A, B](fa: ModifyResult[A])
                           (f: A => ModifyResult[B]): ModifyResult[B] =
      fa.flatMap(f)

    override def point[A](a: => A): ModifyResult[A] =
      ModifyResult(a, modifiedCount = 0)

  }

}