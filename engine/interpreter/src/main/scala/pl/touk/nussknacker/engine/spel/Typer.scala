package pl.touk.nussknacker.engine.spel

import java.math.BigInteger

import cats.data.NonEmptyList._
import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
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

private[spel] class Typer(classLoader: ClassLoader) {

  def typeExpression(expr: Expression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, TypingResult] = {
    expr match {
      case e:standard.SpelExpression =>
        typeExpression(e, ctx)
      case e:CompositeStringExpression =>
        val validatedParts = e.getExpressions.toList.map(typeExpression(_, ctx)).sequence

        validatedParts.map(_ => Typed[String])
      case e:LiteralExpression =>
        Valid(Typed[String])
    }
  }
  private def typeExpression(spelExpression: standard.SpelExpression, ctx: ValidationContext): ValidatedNel[ExpressionParseError, TypingResult] = {
    Validated.fromOption(Option(spelExpression.getAST), NonEmptyList.of(ExpressionParseError("Empty expression"))).andThen(typeNode(ctx, _, Nil))
  }

  private def typeNode(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])
  : ValidatedNel[ExpressionParseError, TypingResult] = {

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: List[TypingResult]) = typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts: universe.TypeTag](result: TypingResult) = withTypedChildren {
      case list if list.forall(_.canBeSubclassOf(Typed.fromDetailedType[Parts])) => Valid(result)
      case _ => invalid("Wrong part types")
    }

    node match {

      case e: Assign => invalid("Value modifications are not supported")
      case e: BeanReference => invalid("Bean reference is not supported")
      case e: BooleanLiteral => Valid(Typed[Boolean])
      case e: CompoundExpression => e.children match {
        case first :: rest => rest.foldLeft(typeNode(validationContext, first, current)) {
          case (Valid(typ), next) => typeNode(validationContext, next, typ :: current)
          case (invalid, _) => invalid
        }
        //should not happen as CompoundExpression doesn't allow this...
        case Nil => Valid(Unknown)
      }

      //TODO: what should be here?
      case e: ConstructorReference => fixed(Unknown)

      case e: Elvis => withTypedChildren(l => Valid(Typed(l.toSet)))
      case e: FloatLiteral => Valid(Typed[java.lang.Float])
      //TODO: what should be here?
      case e: FunctionReference => Valid(Unknown)

      //TODO: what should be here?
      case e: Identifier => Valid(Unknown)
      //TODO: what should be here?
      case e: Indexer =>
        val result = current match {
          case TypedClass(clazz, param :: Nil) :: Nil if clazz.isAssignableFrom(classOf[java.util.List[_]]) => param
          case TypedClass(clazz, keyParam :: valueParam :: Nil):: Nil if clazz.isAssignableFrom(classOf[java.util.Map[_, _]]) => valueParam
          case _ => Unknown
        }
        Valid(result)
      case e: InlineList => withTypedChildren { children =>
        val childrenTypes = children.toSet
        val genericType = if (childrenTypes.contains(Unknown) || childrenTypes.size != 1) Unknown else childrenTypes.head
        fixed(TypedClass(classOf[java.util.List[_]], List(genericType)))
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
          values.map(typeNode(validationContext, _, current)).sequence.map { typedValues =>
            TypedObjectTypingResult(literalKeys.zip(typedValues).toMap)
          }
        }
      case e: IntLiteral => Valid(Typed[java.lang.Integer])
      //case e:Literal => Valid(classOf[Any])
      case e: LongLiteral => Valid(Typed[java.lang.Long])

      case e: MethodReference =>
        TypeMethodReference(e, current) match {
          case Right(typingResult) => fixedWithNewCurrent(current.tail)(typingResult)
          case Left(errorMsg) => invalid(errorMsg)
        }

      case e: NullLiteral => Valid(Unknown)

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

      case e: Projection => current.headOption match {
        case None => invalid("Cannot do projection here")
        //index, check if can project?
        case Some(iterateType) =>
          val listType = extractListType(iterateType)
          typeChildren(validationContext, node, listType :: current) {
            case result :: Nil => Valid(TypedClass(classOf[java.util.List[_]], List(result)))
            case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
          }
      }

      case e: PropertyOrFieldReference =>
        current.headOption.map(extractProperty(e)).getOrElse {
          invalid(s"Non reference '${e.toStringAST}' occurred. Maybe you missed '#' in front of it?")
        }
      //TODO: what should be here?
      case e: QualifiedIdentifier => fixed(Unknown)

      case e: RealLiteral => Valid(Typed[java.lang.Double])
      case e: Selection => current.headOption match {
        case None => invalid("Cannot do selection here")
        case Some(iterateType) =>
          typeChildren(validationContext, node, extractListType(iterateType) :: current) {
            case result :: Nil if result.canBeSubclassOf(Typed[Boolean]) => Valid(iterateType)
            case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
          }
      }

      case e: StringLiteral => Valid(Typed[String])

      case e: Ternary => withTypedChildren {
        case condition :: onTrue :: onFalse :: Nil if condition.canBeSubclassOf(Typed[Boolean]) => Valid(Typed(onTrue, onFalse))
        case _ => invalid("Invalid ternary operator")
      }
      //TODO: what should be here?
      case e: TypeReference => fixed(Unknown)

      case e: VariableReference =>
        //only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext.get(name).orElse(current.headOption.filter(_ => name == "this")) match {
          case Some(result) => Valid(result)
          case None => invalid(s"Unresolved reference $name")
        }
    }
  }

  private def extractProperty(e: PropertyOrFieldReference)
                             (t: TypingResult): ValidatedNel[ExpressionParseError, TypingResult] = t match {
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

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])(result: TypingResult)
  : ValidatedNel[ExpressionParseError, TypingResult] = {
    typeChildren(validationContext, node, current)(_ => Valid(result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])
                          (result: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult])
  : ValidatedNel[ExpressionParseError, TypingResult] = {
    val data = node.children.map(child => typeNode(validationContext, child, current)).sequence
    data.andThen(result)
  }

  private def invalid[T](message: String): ValidatedNel[ExpressionParseError, T] =
    Invalid(NonEmptyList.of(ExpressionParseError(message)))

  private def commonNumberReference: TypingResult =
    Typed(
      Typed[Double], Typed[Int], Typed[Long], Typed[Float], Typed[Byte], Typed[Short], Typed[BigDecimal], Typed[BigInteger])

  implicit class RichSpelNode(n: SpelNode) {
    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
    }.toList

    def childrenHead: SpelNode = {
      n.getChild(0)
    }

  }

}
