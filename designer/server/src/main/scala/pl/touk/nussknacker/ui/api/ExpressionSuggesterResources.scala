package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.collection.Map
import scala.concurrent.ExecutionContext

class ExpressionSuggesterResources(expressionSuggester: ExpressionSuggester)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser {
  def securedRoute(implicit user: LoggedUser): Route =
    path("expressionSuggestions") {
      post {
        entity(as[ExpressionSuggestionRequest]) { expressionSuggestionRequest =>
          complete {
            expressionSuggester.expressionSuggestions(expressionSuggestionRequest.expression, expressionSuggestionRequest.caretPosition2d, expressionSuggestionRequest.variables)
          }
        }
      }
    }
}

class ExpressionSuggester() {

  def expressionSuggestions(inputValue: String, caretPosition2d: CaretPosition2d, variables: Map[String, RefClazz]): List[ExpressionSuggestion] = {
    val normalized = normalizeMultilineInputToSingleLine(inputValue, caretPosition2d)
    val lastExpressionPart = focusedLastExpressionPartWithoutMethodParens(normalized.normalizedInput, normalized.normalizedCaretPosition)
    val variablesIncludingSelectionOrProjection = variables.view.map { case (key, value) => s"#$key" -> value}.toMap
    getSuggestions(lastExpressionPart, variablesIncludingSelectionOrProjection)
  }

  private def normalizeMultilineInputToSingleLine(inputValue: String, caretPosition2d: CaretPosition2d): Normalized = {
    val rows = inputValue.split("\n")
    val trimmedRows = rows.map { row =>
      val trimmedAtStartRow = row.dropWhile(_ == ' ').mkString("")
      (trimmedAtStartRow, row.length - trimmedAtStartRow.length)
    }
    val beforeCaretInputLength = trimmedRows.take(caretPosition2d.row).map(_._1.length).sum
    val normalizedCaretPosition = caretPosition2d.column - trimmedRows(caretPosition2d.row)._2 + beforeCaretInputLength
    val normalizedInput = trimmedRows.map(_._1).mkString("")
    Normalized(normalizedInput, normalizedCaretPosition)
  }

  private def getSuggestions(value: String, variables: Map[String, RefClazz]): List[ExpressionSuggestion] = {
    val variableNotSelected = variables.keys.exists(variable => variable.toLowerCase.startsWith(value.toLowerCase))

    if (variableNotSelected && value.nonEmpty) {
      val allVariablesWithClazzRefs = variables.map { case (key, value) => ExpressionSuggestion(key, RefClazz(value.refClazzName))}.toList
      filterSuggestionsForInput(allVariablesWithClazzRefs, value)
    } else {
      Nil
    }
  }

  private def filterSuggestionsForInput(variables: List[ExpressionSuggestion], inputValue: String): List[ExpressionSuggestion] = {
    variables.filter(variable => variable.methodName.toLowerCase.contains(inputValue.toLowerCase))
  }

  private def focusedLastExpressionPartWithoutMethodParens(expression: String, caretPosition: Int): String = {
    val withSafeNavigationIgnored = expression.take(caretPosition)
    if (expression.isEmpty) {
      ""
    } else if(withSafeNavigationIgnored.endsWith("#")) {
      "#"
    } else {
      s"#${withSafeNavigationIgnored.split("#").lastOption.getOrElse("")}"
    }

  }
}

case class Normalized(normalizedInput: String, normalizedCaretPosition: Int)

@JsonCodec(decodeOnly = true)
case class CaretPosition2d(row: Int, column: Int)

@JsonCodec(decodeOnly = true)
case class ExpressionSuggestionRequest(expression: String, caretPosition2d: CaretPosition2d, variables: Map[String, RefClazz])
@JsonCodec(encodeOnly = true)
case class ExpressionSuggestion(methodName: String, refClazz: RefClazz, fromClass: Boolean = false)

@JsonCodec
case class RefClazz(refClazzName: String, union: List[RefClazz] = Nil, fields: Map[String, RefClazz] = Map())
