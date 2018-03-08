package pl.touk.nussknacker.engine.compile

import cats.{Applicative, Traverse}
import cats.data.Validated.Valid
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.language.{higherKinds, reflectiveCalls}

case class CompilationResult[+Result](private [compile] val typing: Map[String, ValidationContext],
                                     result: ValidatedNel[ProcessCompilationError, Result]) {

  def map[T](action: Result => T) : CompilationResult[T] = copy(result = result.map(action))

  def distinctErrors: CompilationResult[Result] = copy(result =
    result.leftMap(_.toList.distinct).leftMap(NonEmptyList.fromListUnsafe))

  def variablesInNodes: Map[String, Map[String, TypingResult]] = typing.mapValues(_.variables)

}

//in fact, I'm not quite sure it's really, formally Applicative - but for our purposes it should be ok...
object CompilationResult extends Applicative[CompilationResult] {

  def apply[Result](validatedProcess: ValidatedNel[ProcessCompilationError, Result]) : CompilationResult[Result] = CompilationResult(Map(), validatedProcess)

  override def pure[A](x: A): CompilationResult[A] = CompilationResult(Map(), Valid(x))

  override def ap[A, B](ff: CompilationResult[(A) => B])(fa: CompilationResult[A]): CompilationResult[B] =
    CompilationResult(ff.typing ++ fa.typing, fa.result.ap(ff.result))

  implicit class CompilationResultTraverseOps[T[_]: Traverse, B](traverse: T[CompilationResult[B]]) {
    def sequence: CompilationResult[T[B]] = {
      Traverse[T].sequence[({type V[C] = CompilationResult[C]})#V, B](traverse)(CompilationResult.this)
    }
  }

}
