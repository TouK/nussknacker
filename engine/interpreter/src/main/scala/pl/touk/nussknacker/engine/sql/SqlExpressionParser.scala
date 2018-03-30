package pl.touk.nussknacker.engine.sql

import java.sql.SQLSyntaxErrorException
import java.util

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.lazyy.LazyValuesProvider
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, typing}
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.expression
import pl.touk.nussknacker.engine.compiledgraph.expression.{Expression, ExpressionParser, ValueWithLazyContext}
import pl.touk.nussknacker.engine.sql.CreateColumnModel.{InvalidateMessage, NotAListMessage, UnknownInner}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import cats.data._
import cats.data.Validated._
import cats.implicits._

//TODO: reflection is quite naive. works for case classes and TypedMap
//TODO: requires optimization. Each expression evaluation creates database, inserts data, etc.
object SqlExpressionParser extends ExpressionParser {
  override val languageId: String = "sql"

  override def parse(original: String, ctx: ValidationContext, expectedType: ClazzRef)
  : Validated[NonEmptyList[expression.ExpressionParseError], (typing.TypingResult, expression.Expression)] = {
    val froms: SqlFromsQuery = SqlExpressionParser.parseSqlFromsQuery(original, ctx.variables.keys.toList)
    val columnModel = SqlExpressionParser.createTablesDefinition(ctx, froms, CreateColumnModel(_))
    val validatedColumnModel = validateColumnModel(columnModel)
    validatedColumnModel.andThen { colModel =>
      val returnType = getQueryReturnType(original, colModel)
      returnType.map { typingResult =>
        createExpression(original, colModel, typingResult)
      }
    }
  }

  private[sql] def validateColumnModel(columnModels: Map[String, Validated[InvalidateMessage, ColumnModel]])
  : ValidatedNel[expression.ExpressionParseError, Map[String, ColumnModel]] = {
    columnModels.map {
      case (name, invalid@Invalid(invalidateMesage)) =>
        transform(name, invalidateMesage).invalidNel
      case (name, Valid(colModel)) =>
        (name -> colModel).validNel
    }
      .toList
      .sequenceU
      .map(_.toMap)
  }

  private[sql] def transform(columnModelName: String, invalidateMessage: InvalidateMessage): expression.ExpressionParseError = {
    invalidateMessage match {
      case NotAListMessage(typ) =>
        expression.ExpressionParseError(s"cannot create table from '$columnModelName' $typ is not a list")
      case UnknownInner =>
        expression.ExpressionParseError(s"cannot create table '$columnModelName'. List of Unknown")
    }
  }

  private def createExpression(
                                original: String,
                                colModel: Map[String, ColumnModel],
                                typingResult: TypingResult
                              ): (typing.TypingResult, expression.Expression) = {
    val expression = new SqlExpression(original = original, columnModels = colModel)
    val listResult = TypedClass(classOf[List[_]], List(typingResult))
    (Typed(Set(listResult)), expression)
  }

  private[sql] def getQueryReturnType(original: String, colModel: Map[String, ColumnModel]): Validated[NonEmptyList[expression.ExpressionParseError], TypingResult] = {
    val db = new HsqlSqlQueryableDataBase()
    try {
      db.createTables(colModel)
      Validated.Valid(db.getTypingResult(original))
    } catch {
      case e: SQLSyntaxErrorException =>
        Validated.Invalid(NonEmptyList(expression.ExpressionParseError(e.getMessage), Nil))
    } finally {
      db.close()
    }
  }

  def createTablesDefinition(
                              validationContext: ValidationContext,
                              sqlFromsQuery: SqlFromsQuery,
                              createColumnModel: TypingResult => Validated[InvalidateMessage, ColumnModel]
                            ): Map[String, Validated[InvalidateMessage, ColumnModel]] = {
    validationContext.variables
      .filterKeys {
        sqlFromsQuery.froms.contains
      } mapValues {
      createColumnModel
    }
  }

  def parseSqlFromsQuery(query: String, availableVariables: List[String]): SqlFromsQuery = {
    /*
    That could parse query to AST.
    This approximation returns every variable name witch occurs in query by name
     */
    val froms = availableVariables.filter(v => query.contains(v))
    SqlFromsQuery(froms)
  }

  override def parseWithoutContextValidation(original: String, expectedType: ClazzRef): Validated[expression.ExpressionParseError, expression.Expression] =
    throw new IllegalStateException("shouldn't be useed")

}

case class SqlExpressEvaluationException(notAListExceptions :NonEmptyList[PrepareTables.NotAListException])
  extends IllegalArgumentException(notAListExceptions.toString())

class SqlExpression(val original: String, columnModels: Map[String, ColumnModel]) extends Expression {

  private def unvalidate(value: ValidatedNel[PrepareTables.NotAListException, List[TypedMap]]):List[TypedMap] = {
    value match {
      case Valid(list) => list
      case Invalid(e) => throw SqlExpressEvaluationException(e)
    }
  }

  override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[expression.ValueWithLazyContext[T]] = {
    Future.successful {
      val result = unvalidate(evaluate(ctx)).asJava.asInstanceOf[T]
      ValueWithLazyContext(result, ctx.lazyContext)
    }
  }

  private def evaluate[T](ctx: Context): ValidatedNel[PrepareTables.NotAListException, List[TypedMap]] = {
    val db = new HsqlSqlQueryableDataBase
    db.createTables(columnModels)
    val result = PrepareTables(ctx.variables, columnModels, ReadObjectField)
      .map { tables =>
        db.insertTables(tables)
        db.query(original)
      }
    db.close()
    result
  }
}


case class SqlFromsQuery(froms: List[String])

case class Table(model: ColumnModel, rows: List[List[Any]])

case class ColumnModel(columns: List[Column])

case class Column(name: String, typ: SqlType)

sealed trait SqlType

object SqlType {

  object Numeric extends SqlType

  object Date extends SqlType

  object Varchar extends SqlType

  object Bool extends SqlType

}



