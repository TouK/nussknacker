package pl.touk.nussknacker.engine.compile

import cats.{Applicative, Traverse}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.instances.map._
import cats.kernel.Semigroup
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ProcessUncanonizationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  EmptyProcess,
  InvalidRootNode,
  InvalidTailOfBranch
}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonize
import pl.touk.nussknacker.engine.canonize.{MaybeArtificial, MaybeArtificialExtractor, ProcessUncanonizationNodeError}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.language.{higherKinds, reflectiveCalls}
import scala.reflect.ClassTag

case class CompilationResult[+Result](
    typing: Map[String, NodeTypingInfo],
    result: ValidatedNel[ProcessCompilationError, Result]
) {

  import CompilationResult._

  def andThen[B](f: Result => CompilationResult[B]): CompilationResult[B] =
    result match {
      case Valid(a) =>
        val newResult = f(a)
        newResult.copy(typing = Semigroup.combine(typing, newResult.typing))
      case i @ Invalid(_) => CompilationResult(typing, i)
    }

  def map[T](action: Result => T): CompilationResult[T] = copy(result = result.map(action))

  def distinctErrors: CompilationResult[Result] =
    copy(result = result.leftMap(_.toList.distinct).leftMap(NonEmptyList.fromListUnsafe))

  // node -> variable -> TypingResult
  def variablesInNodes: Map[String, Map[String, TypingResult]] = typing.mapValuesNow(_.inputValidationContext.variables)

  // node -> expressionId -> ExpressionTypingInfo
  def expressionsInNodes: Map[String, Map[String, ExpressionTypingInfo]] = typing.mapValuesNow(_.expressionsTypingInfo)

  def parametersInNodes: Map[String, List[Parameter]] = typing.mapValuesNow(_.parameters).collect { case (k, Some(v)) =>
    (k, v)
  }

}

//in fact, I'm not quite sure it's really, formally Applicative - but for our purposes it should be ok...
object CompilationResult extends Applicative[CompilationResult] {

  def apply[Result](validatedProcess: ValidatedNel[ProcessCompilationError, Result]): CompilationResult[Result] =
    CompilationResult(Map(), validatedProcess)

  override def pure[A](x: A): CompilationResult[A] = CompilationResult(Map(), Valid(x))

  override def ap[A, B](ff: CompilationResult[A => B])(fa: CompilationResult[A]): CompilationResult[B] =
    CompilationResult(Semigroup.combine(fa.typing, ff.typing), fa.result.ap(ff.result))

  implicit class CompilationResultTraverseOps[T[_]: Traverse, B](traverse: T[CompilationResult[B]]) {

    def sequence: CompilationResult[T[B]] = {
      Traverse[T].sequence[CompilationResult, B](traverse)(CompilationResult.this)
    }

  }

  private def fromUncanonizationErrors(
      errors: NonEmptyList[canonize.ProcessUncanonizationError]
  ): NonEmptyList[ProcessCompilationError] = {
    def mapOne(e: canonize.ProcessUncanonizationError): ProcessUncanonizationError = e match {
      case canonize.EmptyProcess                => EmptyProcess
      case canonize.InvalidRootNode(nodeId)     => InvalidRootNode(Set(nodeId))
      case canonize.InvalidTailOfBranch(nodeId) => InvalidTailOfBranch(Set(nodeId))
    }
    def mergeErrors[T <: ProcessUncanonizationError: ClassTag](
        collectedSoFar: NonEmptyList[ProcessUncanonizationError],
        error: ProcessUncanonizationNodeError,
        create: Set[String] => T
    ): NonEmptyList[ProcessUncanonizationError] = {
      val (matching, nonMatching) = collectedSoFar.toList.partition {
        case _: T => true
        case _    => false
      }
      matching match {
        case Nil =>
          NonEmptyList(mapOne(error), nonMatching)
        case nonEmpty =>
          NonEmptyList(create(nonEmpty.flatMap(_.nodeIds).toSet + error.nodeId), nonMatching)
      }
    }
    errors.tail.foldLeft(NonEmptyList.one(mapOne(errors.head)))((acc, error) =>
      error match {
        case canonize.EmptyProcess => EmptyProcess :: acc
        case invalidRoot: canonize.InvalidRootNode =>
          mergeErrors(acc, invalidRoot, InvalidRootNode)
        case invalidTail: canonize.InvalidTailOfBranch =>
          mergeErrors(acc, invalidTail, InvalidTailOfBranch)
      }
    )
  }

  implicit def artificialExtractor[A]: MaybeArtificialExtractor[CompilationResult[A]] =
    (errors: List[canonize.ProcessUncanonizationError], rawValue: CompilationResult[A]) => {
      errors match {
        case Nil => rawValue
        case head :: tail =>
          rawValue.copy(
            typing = rawValue.typing.filterKeysNow(key => !key.startsWith(MaybeArtificial.DummyObjectNamePrefix)),
            result = Invalid(fromUncanonizationErrors(NonEmptyList(head, tail)))
          )
      }
    }

  implicit def mergingTypingInfoSemigroup: Semigroup[NodeTypingInfo] = new Semigroup[NodeTypingInfo] with LazyLogging {

    override def combine(x: NodeTypingInfo, y: NodeTypingInfo): NodeTypingInfo = {
      logger.whenWarnEnabled {
        if (x.inputValidationContext != y.inputValidationContext) {
          logger.warn(
            s"Merging different input validation context for the same node ids: ${x.inputValidationContext} != ${y.inputValidationContext}. " +
              s"This can be a bug in code or duplicated node ids with different input validation contexts"
          )
        }
        val expressionsIntersection = x.expressionsTypingInfo.keySet.intersect(y.expressionsTypingInfo.keySet)
        if (expressionsIntersection.nonEmpty) {
          logger.warn(
            s"Merging expression typing info for the same node ids, overlapping expression ids: ${expressionsIntersection
                .mkString(", ")}. " +
              s"This can be a bug in code or duplicated node ids with same expression ids"
          )
        }
        if (x.parameters.isDefined && y.parameters.isDefined) {
          logger.warn(
            s"Merging different parameters: ${x.parameters} and ${y.parameters}. " +
              s"This can be a bug in code or duplicated node ids with same expression ids"
          )
        }
      }

      // we should be lax here because we want to detect duplicate nodes and context can be different then
      // also process of collecting of expressionsTypingInfo is splitted for some nodes e.g. expressionsTypingInfo for
      // sink parameters is collected in ProcessCompiler but for final expression is in PartSubGraphCompiler
      NodeTypingInfo(
        y.inputValidationContext,
        x.expressionsTypingInfo ++ y.expressionsTypingInfo,
        y.parameters.orElse(x.parameters)
      )
    }

  }

}

case class NodeTypingInfo(
    inputValidationContext: ValidationContext,
    expressionsTypingInfo: Map[String, ExpressionTypingInfo],
    // Currently only parameters for dynamic nodes (implemented by DynamicComponent) are returned
    // They are used on FE, to faster display correct node details modal (without need for additional validation request to BE)
    parameters: Option[List[Parameter]]
)
