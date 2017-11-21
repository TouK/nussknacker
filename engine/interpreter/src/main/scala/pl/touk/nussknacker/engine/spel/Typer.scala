package pl.touk.nussknacker.engine.spel

import java.math.BigInteger

import cats.data.{NonEmptyList, Validated}
import Validated._
import NonEmptyList._
import cats.syntax.monoid._
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast._
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.nussknacker.engine.compiledgraph.typing._
import cats.syntax.traverse._
import cats.instances.list._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef

import scala.reflect.ClassTag

private[spel] class Typer(implicit classLoader: ClassLoader) {

  def typeExpression(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult] = Nil)
    : Validated[NonEmptyList[ExpressionParseError], TypingResult] = {

    val withTypedChildren = typeChildren(validationContext, node, current) _

    def fixedWithNewCurrent(newCurrent: List[TypingResult]) = typeChildrenAndReturnFixed(validationContext, node, newCurrent) _

    val fixed = fixedWithNewCurrent(current)

    def withChildrenOfType[Parts:ClassTag](result: TypingResult) = withTypedChildren {
      case list if list.forall(_.canBeSubclassOf(ClazzRef[Parts])) => Valid(result)
      case _ => invalid("Wrong part types")
    }

    node match {

      case e:Assign => invalid("Value modifications are not supported")
      case e:BeanReference => invalid("Bean reference is not supported")
      case e:BooleanLiteral => Valid(Typed[Boolean])
      case e:CompoundExpression => e.children match {
        case first::rest => rest.foldLeft(typeExpression(validationContext, first, current)) {
          case (Valid(typ), next) => typeExpression(validationContext, next, typ::current)
          case (invalid, _) => invalid
        }
          //should not happen as CompoundExpression doesn't allow this...
        case Nil => Valid(Unknown)
      }

      //TODO: what should be here?
      case e:ConstructorReference => fixed(Unknown)

      case e:Elvis => withTypedChildren(l => Valid(l.reduce(_ |+| _)))
      case e:FloatLiteral => Valid(Typed[java.lang.Float])
      //TODO: what should be here?
      case e:FunctionReference => Valid(Unknown)

      //TODO: what should be here?
      case e:Identifier => Valid(Unknown)
      //TODO: what should be here?
      case e:Indexer => Valid(Unknown)

      case e:InlineList => fixed(Typed[java.util.List[_]])
      case e:InlineMap =>
        val zipped = e.children.zipWithIndex
        val keys = zipped.filter(_._2 % 2 == 0).map(_._1)
        val values = zipped.filter(_._2 % 2 == 1).map(_._1)
        //literal keys are handled separately...
        val nonLiteralKeys = keys.filterNot(_.isInstanceOf[PropertyOrFieldReference])
        (values ++ nonLiteralKeys).map(typeExpression(validationContext, _, current)).sequenceU.map { _ =>
          Typed[java.util.Map[_, _]]
        }

      case e:IntLiteral => Valid(Typed[java.lang.Integer])
      //case e:Literal => Valid(classOf[Any])
      case e:LongLiteral => Valid(Typed[java.lang.Long])

      //TODO: how this should be better validated
      //currently we don't validate that method params are ok, nor return type - but choosing appropriate method (overloading??)
      //may be tricky. Maybe we should do better validation only when no overloading occurs?
      case e:MethodReference => fixedWithNewCurrent(current.tail)(Unknown)

      case e:NullLiteral => Valid(Unknown)

      case e:OpAnd => withChildrenOfType[Boolean](Typed[Boolean])
      case e:OpDec => withChildrenOfType[Number](commonNumberReference)
      //case e:Operator => Valid(classOf[Any])
      case e:OpDivide => withChildrenOfType[Number](commonNumberReference)
      case e:OpEQ => fixed(Typed[Boolean])
      case e:OpGE => withChildrenOfType[Number](Typed[Boolean])
      case e:OpGT => withChildrenOfType[Number](Typed[Boolean])
      case e:OpInc => withChildrenOfType[Number](commonNumberReference)
      case e:OpLE => withChildrenOfType[Number](Typed[Boolean])
      case e:OpLT => withChildrenOfType[Number](Typed[Boolean])
      case e:OpMinus => withChildrenOfType[Number](commonNumberReference)
      case e:OpModulus => withChildrenOfType[Number](commonNumberReference)
      case e:OpMultiply => withChildrenOfType[Number](commonNumberReference)
      case e:OpNE => fixed(Typed[Boolean])
      case e:OpOr => withChildrenOfType[Boolean](Typed[Boolean])

      case e:OpPlus => withTypedChildren {
        case left::right::Nil if left == Unknown || right == Unknown => Valid(Unknown)
        case left::right::Nil if left.canBeSubclassOf(ClazzRef[String]) || right.canBeSubclassOf(ClazzRef[String]) => Valid(Typed[String])
        case left::right::Nil if left.canBeSubclassOf(ClazzRef[Number]) || right.canBeSubclassOf(ClazzRef[Number]) => Valid(commonNumberReference)
        case left::Nil => Valid(left)
        case Nil => invalid("Empty plus")
      }
      case e:OperatorBetween => fixed(Typed[Boolean])
      case e:OperatorInstanceof => fixed(Typed[Boolean])
      case e:OperatorMatches => withChildrenOfType[String](Typed[Boolean])
      case e:OperatorNot => withChildrenOfType[Boolean](Typed[Boolean])
      case e:OperatorPower => withChildrenOfType[Number](commonNumberReference)

      case e:Projection => current.headOption match {
        case None => invalid("Cannot do projection here")
          //index, check if can project?
        case Some(iterateType) => fixedWithNewCurrent(Unknown::current)(iterateType)
      }

      case e:PropertyOrFieldReference => current.headOption match {
        case None => invalid(s"Non reference '${e.toStringAST}' occurred. Maybe you missed '#' in front of it?")
        case Some(Unknown) => Valid(Unknown)
        case Some(typed:Typed) if typed.canBeSubclassOf(ClazzRef[java.util.Map[_, _]]) => Valid(Unknown)
        case Some(typed@Typed(possible)) =>
          val possibleResults =
            possible.flatMap(ki => validationContext.getTypeInfo(ClazzRef(ki.klass)))
          possibleResults.flatMap(_.getMethod(e.getName).map(Typed(_))).toList match {
            case Nil => invalid(s"There is no property '${e.getName}' in ${typed.display}")
            case nonEmpty => Valid(nonEmpty.reduce[TypingResult](_ |+| _))  
          }
      }
      //TODO: what should be here?
      case e:QualifiedIdentifier => fixed(Unknown)

      case e:RealLiteral => Valid(Typed[java.lang.Double])
      case e:Selection => current.headOption match {
        case None => invalid("Cannot do selection here")
          //check if can select?
        case Some(iterateType) => fixedWithNewCurrent(Unknown::current)(iterateType)
      }

      case e:StringLiteral => Valid(Typed[String])

      case e:Ternary => withTypedChildren {
        case condition::onTrue::onFalse::Nil if condition.canBeSubclassOf(ClazzRef[Boolean]) => Valid(onTrue |+| onFalse)
        case _ => invalid("Invalid ternary operator")
      }
      //TODO: what should be here?
      case e:TypeReference => fixed(Unknown)

      case e:VariableReference =>
        //only sane way of getting variable name :|
        val name = e.toStringAST.substring(1)
        validationContext.get(name).orElse(current.headOption.filter(_ => name == "this")) match {
          case Some(result) => Valid(result)
          case None => invalid(s"Unresolved reference $name")
        }
    }
  }

  private def typeChildrenAndReturnFixed(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])(result: TypingResult)
    : Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    typeChildren(validationContext, node, current)(_ => Valid(result))
  }

  private def typeChildren(validationContext: ValidationContext, node: SpelNode, current: List[TypingResult])
                              (result: List[TypingResult] => Validated[NonEmptyList[ExpressionParseError], TypingResult])
    : Validated[NonEmptyList[ExpressionParseError], TypingResult] = {
    val data = node.children.map(child => typeExpression(validationContext, child, current)).sequenceU
    data.andThen(result)
  }

  private def invalid[T](message: String) : Validated[NonEmptyList[ExpressionParseError], T] =
    Invalid(NonEmptyList.of(ExpressionParseError(message)))

  private def commonNumberReference : TypingResult =
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
