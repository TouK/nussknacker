package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.kernel.Semigroup
import cats.instances.map._
import cats.{Applicative, Traverse}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ProcessUncanonizationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonize.{MaybeArtificial, MaybeArtificialExtractor}

import scala.language.{higherKinds, reflectiveCalls}

case class CompilationResult[+Result](private [compile] val typing: Map[String, ValidationContext],
                                     result: ValidatedNel[ProcessCompilationError, Result]) {

  import CompilationResult._

  def andThen[B](f: Result => CompilationResult[B]): CompilationResult[B] =
    result match {
      case Valid(a)       =>
        val newResult = f(a)
        newResult.copy(typing = Semigroup.combine(typing, newResult.typing))
      case i @ Invalid(_) => CompilationResult(typing, i)
    }

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
    CompilationResult(Semigroup.combine(fa.typing, ff.typing), fa.result.ap(ff.result))

  implicit class CompilationResultTraverseOps[T[_]: Traverse, B](traverse: T[CompilationResult[B]]) {
    def sequence: CompilationResult[T[B]] = {
      Traverse[T].sequence[({type V[C] = CompilationResult[C]})#V, B](traverse)(CompilationResult.this)
    }
  }

  implicit def artificialExtractor[A]: MaybeArtificialExtractor[CompilationResult[A]] = new MaybeArtificialExtractor[CompilationResult[A]] {
    override def get(errors: List[ProcessUncanonizationError], rawValue: CompilationResult[A]): CompilationResult[A] = {
      errors match {
        case Nil => rawValue
        case e :: es => rawValue.copy(typing = rawValue.typing - MaybeArtificial.DummyObjectName, result = Invalid(NonEmptyList.of(e, es: _*)))
      }
    }
  }

  implicit def takingLastNodeTypingInfoSemigroup: Semigroup[ValidationContext] = new Semigroup[ValidationContext] with LazyLogging {
    override def combine(x: ValidationContext, y: ValidationContext): ValidationContext = {
      logger.whenWarnEnabled {
        if (x != y) {
          logger.warn(s"Merging different ValidationContext for the same nodes: $x != $y. This can be a bug in code or duplicated node ids with different node typing info")
        }
      }
      // we should be lax here because we want to detect duplicate nodes and context can be different then
      y
    }
  }

}