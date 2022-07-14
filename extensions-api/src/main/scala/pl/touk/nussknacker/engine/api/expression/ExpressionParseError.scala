package pl.touk.nussknacker.engine.api.expression

import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{MethodReference, TypeReference}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{TypedDict, TypingResult}

trait ExpressionParseError {
  def message: String
}

object ExpressionParseError {
  sealed trait ArgumentTypeError extends ExpressionParseError {
    val found: List[TypingResult]
    val functionName: String

    def expectedString: String

    protected def typesToString(types: List[TypingResult]): String =
      types.map(_.display).mkString(", ")

    override def message: String =
      s"Mismatch parameter types. Found $functionName(${typesToString(found)}). Required: $functionName($expectedString)"
  }

  case class NoVarArgumentTypeError(expected: List[TypingResult],
                                    found: List[TypingResult],
                                    functionName: String) extends ArgumentTypeError {
    override def expectedString: String = typesToString(expected)
  }

  case class VarArgumentTypeError(expected: List[TypingResult],
                                  expectedVarArgument: TypingResult,
                                  found: List[TypingResult],
                                  functionName: String) extends ArgumentTypeError {
    override def expectedString: String = typesToString(expected :+ expectedVarArgument) + "..."
  }

  case class ExpressionTypeError(expected: TypingResult, found: TypingResult) extends ExpressionParseError {
    override def message: String = s"Invalid type of expression: expected ${expected.display}, found ${found.display}"
  }

  object InvocationOnUnknownError extends ExpressionParseError {
    override def message: String = s"Method invocation on Unknown is not allowed"
  }

  case class UnknownMethodError(methodName: String, displayableType: String) extends ExpressionParseError {
    override def message: String = s"No method named $methodName in type $displayableType"
  }

  case class UnknownClassError(className: String) extends ExpressionParseError {
    override def message: String = s"Class $className does not exist"
  }

  case class InvalidMethodReference(methodName: String) extends ExpressionParseError {
    override def message: String = s"Invalid method reference $methodName"
  }

  case class TypeReferenceError(refClass: String) extends ExpressionParseError {
    override def message: String = s"$refClass is not allowed to be passed as TypeReference"
  }

  case class IllegalProjectionSelectionError(typ: TypingResult) extends ExpressionParseError {
    override def message: String = s"Cannot do projection/selection on ${typ.display}"
  }

  case class DictIndexCountError(node: SpelNode) extends ExpressionParseError {
    override def message: String = s"Illegal spel construction: ${node.toStringAST}. Dict should be indexed by a single key"
  }

  case class NoDictError(dictId: String) extends ExpressionParseError {
    override def message: String = s"Dict with given id: $dictId not exists"
  }

  private val maxStringListLength = 3

  private def formatStringList(elems: List[String]): String = {
    val formattedElems = elems.map("'" + _ + "'")
    if (formattedElems.length <= maxStringListLength)
      formattedElems.mkString(", ")
    else
      formattedElems.mkString("", ", ", ", ...")
  }

  case class DictLabelError(label: String, possibleLabels: Option[List[String]], dict: TypedDict) extends ExpressionParseError {
    override def message: String = {
      val optionLabels = possibleLabels.map(formatStringList)
      val possibilitiesString = optionLabels.map(" Possible labels are: " + _ + ".").getOrElse("")
      s"Illegal label: '$label' for ${dict.display}.$possibilitiesString"
    }
  }

  case class DictKeyError(key: String, possibleKeys: Option[List[String]], dict: TypedDict) extends ExpressionParseError {
    override def message: String = {
      val optionKeys = possibleKeys.map(formatStringList)
      val possibilitiesString = optionKeys.map(" Possible keys are: " + _ + ".").getOrElse("")
      s"Illegal key: '$key' for ${dict.display}.$possibilitiesString"
    }
  }

  case class OtherError(message: String) extends ExpressionParseError
}
