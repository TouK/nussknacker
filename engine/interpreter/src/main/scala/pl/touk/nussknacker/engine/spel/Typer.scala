package pl.touk.nussknacker.engine.spel

import java.math.BigInteger

import cats.data.NonEmptyList._
import cats.data.Validated._
import cats.data.{NonEmptyList, Validated}
import cats.instances.list._
import cats.syntax.monoid._
import cats.syntax.traverse._
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast._
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.nussknacker.engine.spel.typer.TypeMethodReference
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.reflect.ClassTag

private[spel] class Typer(implicit classLoader: ClassLoader) {

  def typeExpression(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult] = Nil)
  : Validated[NonEmptyList[ExpressionParseError], TypingResult] = {

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: List[TypingResult]) = typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts: ClassTag](result: TypingResult) = withTypedChildren {
      case list if list.forall(_.canBeSubclassOf(ClazzRef[Parts])) => Valid(result)
      case _ => invalid("Wrong part types")
    }

    node match {

      case e: Assign => invalid("Value modifications are not supported")
      case e: BeanReference => invalid("Bean reference is not supported")
      case e: BooleanLiteral => Valid(Typed[Boolean])
      case e: CompoundExpression => e.children match {
        case first :: rest => rest.foldLeft(typeExpression(validationContext, first, current)) {
          case (Valid(typ), next) => typeExpression(validationContext, next, typ :: current)
          case (invalid, _) => invalid
        }
        //should not happen as CompoundExpression doesn't allow this...
        case Nil => Valid(Unknown)
      }

      //TODO: what should be here?
      case e: ConstructorReference => fixed(Unknown)

      case e: Elvis => withTypedChildren(l => Valid(l.reduce(_ |+| _)))
      case e: FloatLiteral => Valid(Typed[java.lang.Float])
      //TODO: what should be here?
      case e: FunctionReference => Valid(Unknown)

      //TODO: what should be here?
      case e: Identifier => Valid(Unknown)
      //TODO: what should be here?
      case e: Indexer => Valid(Unknown)

      case e: InlineList => withTypedChildren { children =>
        val childrenTypes = children.toSet
        val genericType = if (childrenTypes.contains(Unknown) || childrenTypes.size != 1) Unknown else childrenTypes.head
        fixed(Typed(Set(TypedClass(classOf[java.util.List[_]], List(genericType)))))
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
          values.map(typeExpression(validationContext, _, current)).sequence.map { typedValues =>
            TypedMapTypingResult(literalKeys.zip(typedValues).toMap)
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
        case left :: right :: Nil if left.canBeSubclassOf(ClazzRef[String]) || right.canBeSubclassOf(ClazzRef[String]) => Valid(Typed[String])
        case left :: right :: Nil if left.canBeSubclassOf(ClazzRef[Number]) || right.canBeSubclassOf(ClazzRef[Number]) => Valid(commonNumberReference)
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
            case result :: Nil => Valid(Typed(Set(TypedClass(classOf[java.util.List[_]], List(result)))))
            case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
          }
      }

      case e: PropertyOrFieldReference =>
        current.headOption match {
        case None => invalid(s"Non reference '${e.toStringAST}' occurred. Maybe you missed '#' in front of it?")
        case Some(Unknown) => Valid(Unknown)
        case Some(typed: Typed) if typed.canBeSubclassOf(ClazzRef[java.util.Map[_, _]]) => Valid(Unknown)
        case Some(typed: TypedMapTypingResult) =>
          typed.fields.get(e.getName) match {
            case None => invalid(s"There is no property '${e.getName}' in ${typed.display}")
            case Some(result) => Valid(result)
          }
        case Some(typed@Typed(possible)) =>
          val clazzDefinitions = possible.map(typedClass =>
            EspTypeUtils.clazzDefinition(typedClass.klass)(ClassExtractionSettings.Default)
          ).toList

          clazzDefinitions match {
            //in normal circumstances this should not happen, however we'd rather omit some errors than not allow correct expression
            case Nil =>
              Valid(Unknown)
            case _ =>
              clazzDefinitions.flatMap(_.getPropertyOrFieldClazzRef(e.getName).map(Typed(_))) match {
                case Nil =>
                  invalid(s"There is no property '${e.getName}' in ${typed.display}")
                case nonEmpty =>
                  val reduced = nonEmpty.reduce[TypingResult](_ |+| _)
                  Valid(reduced)
            }
          }

      }
      //TODO: what should be here?
      case e: QualifiedIdentifier => fixed(Unknown)

      case e: RealLiteral => Valid(Typed[java.lang.Double])
      case e: Selection => current.headOption match {
        case None => invalid("Cannot do selection here")
        case Some(iterateType) =>
          typeChildren(validationContext, node, extractListType(iterateType) :: current) {
            case result :: Nil if result.canBeSubclassOf(ClazzRef[Boolean]) => Valid(iterateType)
            case other => invalid(s"Wrong selection type: ${other.map(_.display)}")
          }
      }

      case e: StringLiteral => Valid(Typed[String])

      case e: Ternary => withTypedChildren {
        case condition :: onTrue :: onFalse :: Nil if condition.canBeSubclassOf(ClazzRef[Boolean]) => Valid(onTrue |+| onFalse)
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

  private def extractListType(parent: TypingResult): TypingResult = parent match {
    case Typed(klases) if parent.canBeSubclassOf(ClazzRef[java.util.List[_]]) =>
      //FIXME: what if more results are present?
      klases.headOption.flatMap(_.params.headOption).getOrElse(Unknown)
    case _ => Unknown
  }

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])(result: TypingResult)
  : Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    typeChildren(validationContext, node, current)(_ => Valid(result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])
                          (result: List[TypingResult] => Validated[NonEmptyList[ExpressionParseError], TypingResult])
  : Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    val data = node.children.map(child => typeExpression(validationContext, child, current)).sequence
    data.andThen(result)
  }

  private def invalid[T](message: String): Validated[NonEmptyList[ExpressionParseError], T] =
    Invalid(NonEmptyList.of(ExpressionParseError(message)))

  private def commonNumberReference: TypingResult =
    Typed[Double] |+| Typed[Int] |+| Typed[Long] |+| Typed[Float] |+| Typed[Byte] |+| Typed[Short] |+| Typed[BigDecimal] |+| Typed[BigInteger]

  implicit class RichSpelNode(n: SpelNode) {
    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
    }.toList

    def childrenHead: SpelNode = {
      n.getChild(0)
    }

  }

}
