package pl.touk.nussknacker.engine.sql

import java.sql.SQLSyntaxErrorException

import cats.data.Validated._
import cats.data._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.lazyy.LazyValuesProvider
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, typing}
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.expression
import pl.touk.nussknacker.engine.compiledgraph.expression.{Expression, ExpressionParser, ValueWithLazyContext}
import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel
import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel.InvalidateMessage
import pl.touk.nussknacker.engine.sql.preparevalues.{PrepareTables, ReadObjectField}

import scala.collection.JavaConverters._
import scala.concurrent.Future

//TODO: reflection is quite naive. works for case classes and TypedMap
object SqlExpressionParser extends ExpressionParser {

  override val languageId: String = "sql"

  override def parse(original: String, ctx: ValidationContext, expectedType: ClazzRef)
  : Validated[NonEmptyList[expression.ExpressionParseError], (typing.TypingResult, SqlExpression)] = {
    val columnModel = ctx.variables.mapValues(CreateColumnModel(_))

    val validVars = columnModel.collect {
      case (key, Valid(model)) => key -> model
    }

    getQueryReturnType(original, validVars).map { typingResult =>
      val minimalModel: Map[String, ColumnModel] = findUsedVariables(original, validVars)
      createExpression(original, minimalModel, typingResult)
    }
  }

  //we try to remove each table/variable and check if query still validates.
  private def findUsedVariables(original: String, validVars: Map[String, ColumnModel]): Map[String, ColumnModel] = {
    validVars.filterNot { case (nextVar, _) =>
      getQueryReturnType(original, validVars - nextVar).isValid
    }
  }

  private def createExpression(
                                original: String,
                                colModel: Map[String, ColumnModel],
                                typingResult: TypingResult
                              ): (typing.TypingResult, SqlExpression) = {

    val expression = new SqlExpression(original = original, columnModels = colModel)
    val listResult = TypedClass(classOf[List[_]], List(typingResult))
    (Typed(Set(listResult)), expression)
  }

  private def getQueryReturnType(original: String, colModel: Map[String, ColumnModel]): Validated[NonEmptyList[expression.ExpressionParseError], TypingResult] = {
    val db = new HsqlSqlQueryableDataBase(original, colModel)
    try {
      Validated.Valid(db.getTypingResult)
    } catch {
      case e: SQLSyntaxErrorException =>
        Validated.Invalid(NonEmptyList(expression.ExpressionParseError(e.getMessage), Nil))
    } finally {
      db.close()
    }
  }

  override def parseWithoutContextValidation(original: String, expectedType: ClazzRef): Validated[expression.ExpressionParseError, expression.Expression] =
    throw new IllegalStateException("shouldn't be used")

}

case class SqlExpressEvaluationException(notAListExceptions :NonEmptyList[PrepareTables.NotAListException])
  extends IllegalArgumentException(notAListExceptions.toString())

//FIXME: take care of cleaning up, current implementation may lead to resource leaks...
class SqlExpression(private[sql] val columnModels: Map[String, ColumnModel],
                     val original: String) extends Expression {

  private val databaseHolder = new ThreadLocal[SqlQueryableDataBase] {
    override def initialValue(): SqlQueryableDataBase = newDatabase()
  }

  private def newDatabase(): SqlQueryableDataBase = synchronized {
    new HsqlSqlQueryableDataBase(original, columnModels)
  }

  override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[expression.ValueWithLazyContext[T]] = {
    Future.successful {
      val result = evaluate(ctx).asJava.asInstanceOf[T]
      ValueWithLazyContext(result, ctx.lazyContext)
    }
  }

  private def evaluate[T](ctx: Context): List[TypedMap] = {
    val db = databaseHolder.get()
    PrepareTables(ctx.variables, columnModels)
      .map(db.query)
      .valueOr(error => throw SqlExpressEvaluationException(error))
  }

}


case class Table(model: ColumnModel, rows: List[List[Any]])

case class ColumnModel(columns: List[Column])

case class Column(name: String, typ: SqlType)

sealed trait SqlType

object SqlType {

  object Numeric extends SqlType

  object Decimal extends SqlType

  object Date extends SqlType

  object Varchar extends SqlType

  object Bool extends SqlType

}



