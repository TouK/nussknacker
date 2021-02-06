package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.map._
import cats.kernel.Semigroup
import cats.{Applicative, Traverse}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ProcessUncanonizationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonize.{MaybeArtificial, MaybeArtificialExtractor}

import scala.language.{higherKinds, reflectiveCalls}

case class CompilationResult[+Result](typing: Map[String, NodeTypingInfo],
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

  // node -> variable -> TypingResult
  def variablesInNodes: Map[String, Map[String, TypingResult]] = typing.mapValues(_.inputValidationContext.variables)

  // node -> expressionId -> ExpressionTypingInfo
  def expressionsInNodes: Map[String, Map[String, ExpressionTypingInfo]] = typing.mapValues(_.expressionsTypingInfo)

  def parametersInNodes: Map[String, List[Parameter]] = typing.mapValues(_.parameters).collect {
    case (k, Some(v)) => (k, v)
  }

}

//in fact, I'm not quite sure it's really, formally Applicative - but for our purposes it should be ok...
object CompilationResult extends Applicative[CompilationResult] {

  def apply[Result](validatedProcess: ValidatedNel[ProcessCompilationError, Result]) : CompilationResult[Result] = CompilationResult(Map(), validatedProcess)

  override def pure[A](x: A): CompilationResult[A] = CompilationResult(Map(), Valid(x))

  override def ap[A, B](ff: CompilationResult[A => B])(fa: CompilationResult[A]): CompilationResult[B] =
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

  implicit def mergingTypingInfoSemigroup: Semigroup[NodeTypingInfo] = new Semigroup[NodeTypingInfo] with LazyLogging {
    override def combine(x: NodeTypingInfo, y: NodeTypingInfo): NodeTypingInfo = {
      logger.whenWarnEnabled {
        if (x.inputValidationContext != y.inputValidationContext) {
          logger.warn(s"Merging different input validation context for the same node ids: ${x.inputValidationContext} != ${y.inputValidationContext}. " +
            s"This can be a bug in code or duplicated node ids with different input validation contexts")
        }
        val expressionsIntersection = x.expressionsTypingInfo.keySet.intersect(y.expressionsTypingInfo.keySet)
        if (expressionsIntersection.nonEmpty) {
          logger.warn(s"Merging expression typing info for the same node ids, overlapping expression ids: ${expressionsIntersection.mkString(", ")}. " +
            s"This can be a bug in code or duplicated node ids with same expression ids")
        }
        if (x.parameters.isDefined && y.parameters.isDefined) {
          logger.warn(s"Merging different parameters: ${x.parameters} and ${y.parameters}. " +
            s"This can be a bug in code or duplicated node ids with same expression ids")
        }
      }

      // we should be lax here because we want to detect duplicate nodes and context can be different then
      // also process of collecting of expressionsTypingInfo is splitted for some nodes e.g. expressionsTypingInfo for
      // sink parameters is collected in ProcessCompiler but for final expression is in PartSubGraphCompiler
      NodeTypingInfo(y.inputValidationContext, x.expressionsTypingInfo ++ y.expressionsTypingInfo, y.parameters.orElse(x.parameters))
    }
  }

}

case class NodeTypingInfo(inputValidationContext: ValidationContext,
                          expressionsTypingInfo: Map[String, ExpressionTypingInfo],
                         //Currently only parameters for dynamic nodes (implemented by GenericNodeTransformation) are returned
                         //They are used on FE, to faster display correct node details modal (without need for additional validation request to BE)
                          parameters: Option[List[Parameter]])

object NodeTypingInfo {

  val ExceptionHandlerNodeId = "$exceptionHandler"

  val DefaultExpressionId = "$expression"

  def branchParameterExpressionId(paramName: String, branch: String): String =
    paramName + "-" + branch

}