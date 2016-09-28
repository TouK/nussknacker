package pl.touk.esp.engine.optics

import monocle._
import monocle.function.Plated
import monocle.function.Plated._
import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.canonicalgraph.{CanonicalProcess, CanonicalTreeNode}

import scala.language.{higherKinds, implicitConversions, reflectiveCalls}
import scala.reflect.ClassTag
import scalaz.std.list._
import scalaz.std.anyVal._
import scalaz.syntax.all._
import scalaz.{Applicative, Monad, State}
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
            case n: FilterNode =>
              n.nextFalse.traverse(f).map { cn =>
                n.copy(nextFalse = cn.asInstanceOf[List[CanonicalNode]])
              }
            case n: SwitchNode =>
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

  def select(nodeId: String): Option[CanonicalNode] = {
    universe(process.asInstanceOf[CanonicalTreeNode]).collectFirst {
      case e: CanonicalNode if e.id == nodeId => e
    }
  }

  def modify(nodeId: String)(f: CanonicalNode => CanonicalNode): ModifyResult[CanonicalProcess] = {
    val (count, result) = transform[CanonicalTreeNode, ({type S[A] = State[Int, A]})#S] {
      case e: CanonicalNode if e.id == nodeId =>
        State[Int, CanonicalTreeNode](count => (count + 1, restoreStructure(f(e), e)))
      case other =>
        State.state[Int, CanonicalTreeNode](other)
    }(process).runZero
    ModifyResult(result.asInstanceOf[CanonicalProcess], count)
  }

  private def restoreStructure[T <: CanonicalNode](node: T, fromNode: T) =
    (node: CanonicalNode) match {
      case n: FilterNode =>
        n.copy(nextFalse = fromNode.asInstanceOf[FilterNode].nextFalse)
      case n: SwitchNode =>
        val fromSwitch = fromNode.asInstanceOf[SwitchNode]
        n.copy(nexts = fromSwitch.nexts, defaultNext = fromSwitch.defaultNext)
      case _: FlatNode=>
        node
    }

  private def transform[A: Plated, M[_]: Monad](f: A => M[A])(a: A): M[A] = {
    val l = plate[A]
    def go(c: A): M[A] =
      l.modifyF[M](b => f(b).flatMap(go))(c)
    go(a)
  }

}

object ProcessOptics {

  case class ModifyResult[V](value: V, modifiedCount: Int)

}