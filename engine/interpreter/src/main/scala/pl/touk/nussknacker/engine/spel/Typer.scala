package pl.touk.nussknacker.engine.spel

import java.math.BigInteger

import cats.data.NonEmptyList._
import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import Typer._
import cats.instances.map._
import cats.kernel.{Monoid, Semigroup}
import cats.syntax.traverse._
import org.springframework.expression.Expression
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.ast._
import org.springframework.expression.spel.{SpelNode, standard}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.spel.typer.TypeMethodReference
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.reflect.runtime._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.spel.ast.SpelNodePrettyPrinter

import scala.util.{Failure, Success}

private[spel] class Typer(implicit classLoader: ClassLoader) extends LazyLogging {

  def typeExpression(expr: Expression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, TypingResult] = {
    typeExpressionInternal(expr, ctx).map(_.finalResult)
  }

  def typeExpressionInternal(expr: Expression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    expr match {
      case e:standard.SpelExpression =>
        typeExpression(e, ctx)
      case e:CompositeStringExpression =>
        val validatedParts = e.getExpressions.toList.map(typeExpressionInternal(_, ctx)).sequence

        validatedParts.map(partResults => CollectedTypingResult(Monoid.combineAll(partResults.map(_.intermediateResults)), Typed[String]))
      case e:LiteralExpression =>
        Valid(CollectedTypingResult.withEmptyIntermediateResults(Typed[String]))
    }
  }

  private def typeExpression(spelExpression: standard.SpelExpression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    Validated.fromOption(Option(spelExpression.getAST), NonEmptyList.of(ExpressionParseError("Empty expression"))).andThen { ast =>
      val result = typeNode(ctx, ast)
      logger.whenTraceEnabled {
        result match {
          case Valid(collectedResult) =>
            val printer = new SpelNodePrettyPrinter(n => collectedResult.intermediateResults.get(n).map(_.display).getOrElse("NOT_TYPED"))
            logger.trace("typed valid expression: " + printer.print(ast))
          case Invalid(errors) =>
            logger.trace(s"typed invalid expression: ${spelExpression.getExpressionString}, errors: ${errors.toList.mkString(", ")}")
        }
      }
      result
    }
  }

  private def typeNode(validationContext: ValidationContext, node: SpelNode): ValidatedNel[ExpressionParseError, CollectedTypingResult] =
    typeNode(validationContext, node, TypingContext(List.empty, Map.empty))

  private def typeNode(validationContext: ValidationContext, node: SpelNode, current: TypingContext)
  : ValidatedNel[ExpressionParseError, CollectedTypingResult] = {

    def toResult(typ: TypingResult) = current.toResult(TypedNode(node, typ))

    def valid(typ: TypingResult) = Valid(toResult(typ))

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: TypingContext) = typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts: universe.TypeTag](result: TypingResult) = withTypedChildren {
      case list if list.forall(_.canBeSubclassOf(Typed.fromDetailedType[Parts])) => Valid(result)
      case _ => invalid("Wrong part types")
    }

    node match {

      case e: Assign => invalid("Value modifications are not supported")
      case e: BeanReference => invalid("Bean reference is not supported")
      case e: BooleanLiteral => valid(Typed[Boolean])
      case e: CompoundExpression => e.children match {
        case first :: rest =>
          val validatedLastType = rest.foldLeft(typeNode(validationContext, first, current)) {
            case (Valid(prevResult), next) => typeNode(validationContext, next, current.pushOnStack(prevResult))
            case (invalid, _) => invalid
          }
          validatedLastType.map { lastType =>
            CollectedTypingResult(lastType.intermediateResults + (e -> lastType.finalResult), lastType.finalResult)
          }
        //should not happen as CompoundExpression doesn't allow this...
        case Nil => valid(Unknown)
      }

      //TODO: what should be here?
      case e: ConstructorReference => fixed(Unknown)

      case e: Elvis => withTypedChildren(l => Valid(Typed(l.toSet)))
      case e: FloatLiteral => valid(Typed[java.lang.Float])
      //TODO: what should be here?
      case e: FunctionReference => valid(Unknown)

      //TODO: what should be here?
      case e: Identifier => valid(Unknown)
      //TODO: what should be here?
      case e: Indexer =>
        val result = current.stack match {
          case TypedClass(clazz, param :: Nil) :: Nil if clazz.isAssignableFrom(classOf[java.util.List[_]]) => param
          case TypedClass(clazz, keyParam :: valueParam :: Nil):: Nil if clazz.isAssignableFrom(classOf[java.util.Map[_, _]]) => valueParam
          case _ => Unknown
        }
        valid(result)
      case e: InlineList => withTypedChildren { children =>
        val childrenTypes = children.toSet
        val genericType = if (childrenTypes.contains(Unknown) || childrenTypes.size != 1) Unknown else childrenTypes.head
        Valid(TypedClass(classOf[java.util.List[_]], List(genericType)))
      }

      case e: InlineMap =>
        val zipped = e.children.zipWithIndex
        val keys = zipped.filter(_._2 % 2 == 0).map(_._1)
        val values = zipped.filter(_._2 % 2 == 1).map(_._1)
        val literalKeys = keys
          .collect {
            case a:PropertyOrFieldReference => a.getName
            case b:StringLiteral => b.getLiteralValue.getValue.toString
          }

        if (literalKeys.size != keys.size) {
          invalid("Currently inline maps with not literal keys (e.g. expressions as keys) are not supported")
        } else {
          values.map(typeNode(validationContext, _, current.withoutIntermediateResults)).sequence.andThen { typedValues =>
            withCombinedIntermediate(typedValues, current) { typedValues =>
              val typ = TypedObjectTypingResult(literalKeys.zip(typedValues).toMap)
              Valid(TypedNode(node, typ))
            }
          }
        }
      case e: IntLiteral => valid(Typed[java.lang.Integer])
      //case e:Literal => Valid(classOf[Any])
      case e: LongLiteral => valid(Typed[java.lang.Long])

      case e: MethodReference =>
        TypeMethodReference(e, current.stack) match {
          case Right(typingResult) => fixedWithNewCurrent(current.popStack)(typingResult)
          case Left(errorMsg) => invalid(errorMsg)
        }

      case e: NullLiteral => valid(Unknown)

      case e: OpAnd => withChildrenOfType[Boolean](Typed[Boolean])
      case e: OpDec => withChildrenOfType[Number](commonNumberReference)
      //case e:Operator => Valid(classOf[Any])
      case e: OpDivide => withChildrenOfType[Number](commonNumberReference)
      case e: OpEQ => fixed(Typed[Boolean])
      case e: OpGE => withChildrenOfType[Number](Typed[Boolean])
      case e: OpGT => withChildrenOfType[Number](Typed[Boolean])
      case e: OpInc => withChildrenOfType[Number](commonNumberReference)
      case e: OpLE => withChildrenOfType[Number](Typed[Boolean])
      case e: OpLT => withChildrenOfType[Number](Typed[Boolean])
      case e: OpMinus => withChildrenOfType[Number](commonNumberReference)
      case e: OpModulus => withChildrenOfType[Number](commonNumberReference)
      case e: OpMultiply => withChildrenOfType[Number](commonNumberReference)
      case e: OpNE => fixed(Typed[Boolean])
      case e: OpOr => withChildrenOfType[Boolean](Typed[Boolean])

      case e: OpPlus => withTypedChildren {
        case left :: right :: Nil if left == Unknown || right == Unknown => Valid(Unknown)
        case left :: right :: Nil if left.canBeSubclassOf(Typed[String]) || right.canBeSubclassOf(Typed[String]) => Valid(Typed[String])
        case left :: right :: Nil if left.canBeSubclassOf(Typed[Number]) || right.canBeSubclassOf(Typed[Number]) => Valid(commonNumberReference)
        case left :: Nil => Valid(left)
        case Nil => invalid("Empty plus")
      }
      case e: OperatorBetween => fixed(Typed[Boolean])
      case e: OperatorInstanceof => fixed(Typed[Boolean])
      case e: OperatorMatches => withChildrenOfType[String](Typed[Boolean])
      case e: OperatorNot => withChildrenOfType[Boolean](Typed[Boolean])
      case e: OperatorPower => withChildrenOfType[Number](commonNumberReference)

      case e: Projection => current.stackHead match {
        case None => invalid("Cannot do projection here")
        //index, check if can project?
        case Some(iterateType) =>
          val listType = extractListType(iterateType)
          typeChildren(validationContext, node, current.pushOnStack(listType)) {
            case result :: Nil => Valid(TypedClass(classOf[java.util.List[_]], List(result)))
            case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
          }
      }

      case e: PropertyOrFieldReference =>
        current.stackHead.map(head => extractProperty(e, head).map(toResult)).getOrElse {
          invalid(s"Non reference '${e.toStringAST}' occurred. Maybe you missed '#' in front of it?")
        }
      //TODO: what should be here?
      case e: QualifiedIdentifier => fixed(Unknown)

      case e: RealLiteral => valid(Typed[java.lang.Double])
      case e: Selection => current.stackHead match {
        case None => invalid("Cannot do selection here")
        case Some(iterateType) =>
          typeChildren(validationContext, node, current.pushOnStack(extractListType(iterateType))) {
            case result :: Nil if result.canBeSubclassOf(Typed[Boolean]) => Valid(iterateType)
            case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
          }
      }

      case e: StringLiteral => valid(Typed[String])

      case e: Ternary => withTypedChildren {
        case condition :: onTrue :: onFalse :: Nil if condition.canBeSubclassOf(Typed[Boolean]) => Valid(Typed(onTrue, onFalse))
        case _ => invalid("Invalid ternary operator")
      }
      //TODO: what should be here?
      case e: TypeReference => fixed(Unknown)

      case e: VariableReference =>
        //only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext.get(name).orElse(current.stackHead.filter(_ => name == "this")) match {
          case Some(result) => valid(result)
          case None => invalid(s"Unresolved reference $name")
        }
    }
  }

  private def extractProperty(e: PropertyOrFieldReference, t: TypingResult): ValidatedNel[ExpressionParseError, TypingResult] = t match {
    case typed: TypingResult if typed.canHasAnyPropertyOrField => Valid(Unknown)
    case Unknown => Valid(Unknown)
    case s: SingleTypingResult =>
      extractSingleProperty(e)(s)
        .map(Valid(_)).getOrElse(invalid(s"There is no property '${e.getName}' in type: ${s.display}"))
    case TypedUnion(possible) =>
      val l = possible.toList.flatMap(single => extractSingleProperty(e)(single))
      if (l.isEmpty)
        invalid(s"There is no property '${e.getName}' in type: ${t.display}")
      else
        Valid(Typed(l.toSet))
  }

  private def extractSingleProperty(e: PropertyOrFieldReference)
                                   (t: SingleTypingResult) = t match {
    case typed: SingleTypingResult if typed.canHasAnyPropertyOrField => Some(Unknown)
    case typedClass: TypedClass =>
      val clazzDefinition = EspTypeUtils.clazzDefinition(typedClass.klass)(ClassExtractionSettings.Default)
      clazzDefinition.getPropertyOrFieldClazzRef(e.getName).map(Typed(_))
    case typed: TypedObjectTypingResult =>
      typed.fields.get(e.getName)
  }

  private def extractListType(parent: TypingResult): TypingResult = parent match {
    case tc: TypedClass if tc.canBeSubclassOf(Typed[java.util.List[_]]) =>
      tc.params.headOption.getOrElse(Unknown)
    //FIXME: what if more results are present?
    case _ => Unknown
  }

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: TypingContext)(result: TypingResult)
  : Validated[NonEmptyList[ExpressionParseError], CollectedTypingResult] = {
    typeChildren(validationContext, node, current)(_ => Valid(result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: TypingContext)
                          (result: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult])
  : ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val data = node.children.map(child => typeNode(validationContext, child, current.withoutIntermediateResults)).sequence
    data.andThen { collectedChildrenResults =>
      withCombinedIntermediate(collectedChildrenResults, current) { childrenResults =>
        result(childrenResults).map(TypedNode(node, _))
      }
    }
  }

  private def withCombinedIntermediate(intermediate: List[CollectedTypingResult], current: TypingContext)
                                      (result: List[TypingResult] => ValidatedNel[ExpressionParseError, TypedNode])
  : ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    val intermediateResultsCombination = Monoid.combineAll(current.intermediateResults :: intermediate.map(_.intermediateResults))
    val intermediateTypes = intermediate.map(_.finalResult)
    result(intermediateTypes).map(CollectedTypingResult.withIntermediateAndFinal(intermediateResultsCombination, _))
  }

  private def invalid[T](message: String): ValidatedNel[ExpressionParseError, T] =
    Invalid(NonEmptyList.of(ExpressionParseError(message)))

  private def commonNumberReference: TypingResult =
    Typed(
      Typed[Double], Typed[Int], Typed[Long], Typed[Float], Typed[Byte], Typed[Short], Typed[BigDecimal], Typed[BigInteger])

}

object Typer {

  implicit class RichSpelNode(n: SpelNode) {

    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
      }.toList

    def childrenHead: SpelNode = {
      n.getChild(0)
    }

  }

  implicit def notAcceptingMergingSemigroup: Semigroup[TypingResult] = new Semigroup[TypingResult] with LazyLogging {
    override def combine(x: TypingResult, y: TypingResult): TypingResult = {
      assert(x == y, "Types not matching during combination of types for spel nodes")
      // merging the same types is not bad but it is a warning that sth went wrong e.g. typer typed something more than one time
      // or spel node's identity is broken
      logger.warn(s"Merging same types: $x for the same nodes. This shouldn't happen")
      x
    }
  }

}

case class CollectedTypingResult(intermediateResults: Map[SpelNode, TypingResult], finalResult: TypingResult)

object CollectedTypingResult {

  def withEmptyIntermediateResults(finalResult: TypingResult): CollectedTypingResult =
    CollectedTypingResult(Map.empty, finalResult)

  def withIntermediateAndFinal(intermediateResults: Map[SpelNode, TypingResult], finalNode: TypedNode): CollectedTypingResult = {
    CollectedTypingResult(intermediateResults + (finalNode.node -> finalNode.typ), finalNode.typ)
  }

}

// It contains stack of types for recognition of nested node type
// intermediateResults are all results that we can collect for intermediate nodes
case class TypingContext(stack: List[TypingResult], intermediateResults: Map[SpelNode, TypingResult]) {

  def pushOnStack(typingResult: TypingResult): TypingContext = copy(stack = typingResult :: stack)

  def pushOnStack(typingResult: CollectedTypingResult): TypingContext =
    TypingContext(typingResult.finalResult :: stack, intermediateResults ++ typingResult.intermediateResults)

  def popStack: TypingContext = copy(stack = stack.tail)

  def stackHead: Option[TypingResult] = stack.headOption

  def stackTail: List[TypingResult] = stack.tail

  def withoutIntermediateResults: TypingContext = copy(intermediateResults = Map.empty)

  def toResult(finalNode: TypedNode): CollectedTypingResult =
    CollectedTypingResult(intermediateResults + (finalNode.node -> finalNode.typ), finalNode.typ)

}

case class TypedNode(node: SpelNode, typ: TypingResult)