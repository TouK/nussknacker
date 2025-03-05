package pl.touk.nussknacker.engine.spel

import cats.data.NonEmptyList
import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, Signature}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{TypedDict, TypingResult}

object SpelExpressionParseError {
  trait SelectionProjectionError extends ExpressionParseError

  object SelectionProjectionError {

    case object IllegalSelectionError extends SelectionProjectionError {
      override def message: String = "Cannot do selection here"
    }

    case object IllegalProjectionError extends SelectionProjectionError {
      override def message: String = "Cannot do projection here"
    }

    case class IllegalSelectionTypeError(types: List[TypingResult]) extends SelectionProjectionError {
      override def message: String = s"Wrong selection type: ${types.map(_.display)}"
    }

  }

  trait UnsupportedOperationError extends ExpressionParseError

  object UnsupportedOperationError {

    case object ModificationError extends UnsupportedOperationError {
      override def message: String = "Value modifications are not supported"
    }

    case object BeanReferenceError extends UnsupportedOperationError {
      override def message: String = "Bean reference is not supported"
    }

    case object MapWithExpressionKeysError extends UnsupportedOperationError {

      override def message: String = {
        "Currently only string keys for inline maps are supported"
      }

    }

    case object ArrayConstructorError extends UnsupportedOperationError {
      override def message: String = "Array constructor is not supported"
    }

  }

  trait DictError extends ExpressionParseError {

    protected def formatStringList(elems: List[String]): String = {
      val maxStringListLength = 3
      val formattedElems      = elems.map("'" + _ + "'")
      if (formattedElems.length <= maxStringListLength)
        formattedElems.mkString(", ")
      else
        formattedElems.mkString("", ", ", ", ...")
    }

  }

  object DictError {

    case class DictIndexCountError(node: SpelNode) extends DictError {
      override def message: String =
        s"Illegal spel construction: ${node.toStringAST}. Dict should be indexed by a single key"
    }

    case class NoDictError(dictId: String) extends DictError {
      override def message: String = s"Dict with given id: $dictId not exists"
    }

    case class DictLabelError(label: String, possibleLabels: Option[List[String]], dict: TypedDict) extends DictError {

      override def message: String = {
        val optionLabels        = possibleLabels.map(formatStringList)
        val possibilitiesString = optionLabels.map(" Possible labels are: " + _ + ".").getOrElse("")
        s"Illegal label: '$label' for ${dict.display}.$possibilitiesString"
      }

    }

    case class DictKeyError(key: String, possibleKeys: Option[List[String]], dict: TypedDict) extends DictError {

      override def message: String = {
        val optionKeys          = possibleKeys.map(formatStringList)
        val possibilitiesString = optionKeys.map(" Possible keys are: " + _ + ".").getOrElse("")
        s"Illegal key: '$key' for ${dict.display}.$possibilitiesString"
      }

    }

  }

  trait MissingObjectError extends ExpressionParseError

  object MissingObjectError {

    case class NoPropertyError(typ: TypingResult, property: String) extends MissingObjectError {
      override def message: String = s"There is no property '$property' in type: ${typ.display}"
    }

    case class NoPropertyTypeError(typ: TypingResult, propertyType: TypingResult) extends MissingObjectError {
      override def message: String = s"There is no property of type '${propertyType.display}' in type: ${typ.display}"
    }

    case class UnknownMethodError(methodName: String, displayableType: String) extends MissingObjectError {
      override def message: String = s"Unknown method '$methodName' in $displayableType"
    }

    case class UnknownClassError(className: String) extends MissingObjectError {
      override def message: String = s"Class $className does not exist"
    }

    case class ConstructionOfUnknown(clazz: Option[Class[_]]) extends MissingObjectError {
      override def message: String =
        s"Cannot create instance of unknown class" ++ clazz.map(" " ++ _.toString).getOrElse("")
    }

    case class UnresolvedReferenceError(name: String) extends MissingObjectError {
      override def message: String = s"Unresolved reference '$name'"
    }

    case class NonReferenceError(text: String) extends MissingObjectError {
      override def message: String = s"Non reference '$text' occurred. Maybe you missed '#' in front of it?"
    }

  }

  trait IllegalOperationError extends ExpressionParseError

  object IllegalOperationError {

    case class IllegalPropertyAccessError(typ: TypingResult) extends IllegalOperationError {
      override def message: String = s"Property access on ${typ.display} is not allowed"
    }

    case class IllegalInvocationError(typ: TypingResult) extends IllegalOperationError {
      override def message: String = s"Method invocation on ${typ.display} is not allowed"
    }

    case class InvalidMethodReference(methodName: String) extends IllegalOperationError {
      override def message: String = s"Invalid method reference: $methodName."
    }

    case class TypeReferenceError(refClass: String) extends IllegalOperationError {
      override def message: String = s"$refClass is not allowed to be passed as TypeReference"
    }

    case class IllegalProjectionSelectionError(typ: TypingResult) extends IllegalOperationError {
      override def message: String = s"Cannot do projection/selection on ${typ.display}"
    }

    case object DynamicPropertyAccessError extends IllegalOperationError {
      override def message: String = "Dynamic property access is not allowed"
    }

    case object IllegalIndexingOperation extends IllegalOperationError {
      override def message: String = "Cannot do indexing here"
    }

  }

  trait TernaryOperatorError extends OperatorError

  object TernaryOperatorError {

    case class TernaryOperatorNotBooleanError(computedType: TypingResult) extends TernaryOperatorError {
      override def message: String =
        s"Not a boolean expression used in ternary operator (expr ? onTrue : onFalse). Computed expression type: ${computedType.display}"
    }

    case object InvalidTernaryOperator extends TernaryOperatorError {
      override def message: String = "Invalid ternary operator"
    }

  }

  trait OperatorError extends ExpressionParseError

  object OperatorError {

    case class OperatorMismatchTypeError(operator: String, left: TypingResult, right: TypingResult)
        extends OperatorError {
      override def message: String =
        s"Operator '$operator' used with mismatch types: ${left.display} and ${right.display}"
    }

    case class OperatorNonNumericError(operator: String, typ: TypingResult) extends OperatorError {
      override def message: String = s"Operator '$operator' used with non numeric type: ${typ.display}"
    }

    case class OperatorNotComparableError(operator: String, left: TypingResult, right: TypingResult)
        extends OperatorError {
      override def message: String =
        s"Operator '$operator' used with not comparable types: ${left.display} and ${right.display}"
    }

    case class EmptyOperatorError(operator: String) extends OperatorError {
      override def message: String = s"Empty $operator"
    }

    case class BadOperatorConstructionError(operator: String) extends OperatorError {
      override def message: String = s"Bad '$operator' operator construction"
    }

    case class DivisionByZeroError private (expression: String) extends OperatorError {
      override def message: String = s"Division by zero: $expression"
    }

    object DivisionByZeroError {
      def apply(expression: String): DivisionByZeroError =
        new DivisionByZeroError(stripOperatorAST(expression))
    }

    case class ModuloZeroError private (expression: String) extends OperatorError {
      override def message: String = s"Taking remainder modulo zero: $expression"
    }

    object ModuloZeroError {
      def apply(expression: String): ModuloZeroError =
        new ModuloZeroError(stripOperatorAST(expression))
    }

    // Operators AST has form "(expression)" so we need to extract it.
    private def stripOperatorAST(expression: String) =
      expression.substring(1, expression.length - 1)
  }

  object OverloadedFunctionError extends ExpressionParseError {
    override def message: String = "Could not match any overloaded method"
  }

  case class ArgumentTypeError(name: String, found: Signature, possibleSignatures: NonEmptyList[Signature])
      extends ExpressionParseError {

    override def message: String =
      s"Mismatch parameter types. Found: ${found
          .display(name)}. Required: ${possibleSignatures.map(_.display(name)).toList.mkString(" or ")}"

  }

  case class GenericFunctionError(messageInner: String) extends ExpressionParseError {
    override def message: String = messageInner
  }

  case object PartTypeError extends ExpressionParseError {
    override def message: String = "Wrong part types"
  }

  case class ExpressionTypeError(expected: TypingResult, found: TypingResult) extends ExpressionParseError {
    override def message: String = s"Bad expression type, expected: ${expected.display}, found: ${found.display}"
  }

  case class ExpressionCompilationError(message: String) extends ExpressionParseError

  case class KeyWithLabelExpressionParsingError(keyWithLabel: String, message: String) extends ExpressionParseError {

    def toProcessCompilationError(
        nodeId: String,
        paramName: ParameterName
    ): ProcessCompilationError.KeyWithLabelExpressionParsingError =
      ProcessCompilationError.KeyWithLabelExpressionParsingError(keyWithLabel, message, paramName, nodeId)

  }

}
