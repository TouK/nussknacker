package pl.touk.nussknacker.engine.baseengine.kafka

import cats.{Monad, Monoid}
import cats.data.{Writer, WriterT}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.higherKinds


trait EffectCapability {

  type Effect[T]

  def monad: Monad[Effect]

  //???
  def run[T](result: Effect[T]): T

}

trait TransformerEffectCapability extends EffectCapability {

  def add(other: EffectCapability): EffectCapability
}

class WriterEffect[Value: Monoid] extends TransformerEffectCapability {

  type Effect[T] = Writer[Value, T]

  private val that = this

  override def add(other: EffectCapability): EffectCapability = new EffectCapability {

    override type Effect[T] = WriterT[other.Effect, Value, T]

    override def monad: Monad[Effect] =
      WriterT.catsDataMonadForWriterT(other.monad, implicitly[Monoid[Value]])

    override def run[T](result: Effect[T]): T = {
      val (written, resultUnwrapped) = other.run(result.run)
      that.run(Writer(written, resultUnwrapped))
    }
  }

  override def run[T](result: Writer[Value, T]): T = {
    println(result.written)
    result.value
  }

  override def monad: Monad[Effect] = implicitly[Monad[Effect]]
}

//It turns out that
class FutureEffect(ec: ExecutionContext) extends EffectCapability {

  type Effect[T] = Future[T]

  def monad: Monad[Future] = {
    implicit val ec1: ExecutionContext = ec
    implicitly[Monad[Future]]
  }

  override def run[T](result: Future[T]): T = Await.result(result, 10 seconds)
}


object EffectStack extends App {

  val data = List[TransformerEffectCapability](new WriterEffect[List[String]], new WriterEffect[List[Int]])
    .foldRight[EffectCapability](new FutureEffect(SynchronousExecutionContext.ctx))(_.add(_))

  val s = data.monad.pure("ddd")

  println(s)
}

