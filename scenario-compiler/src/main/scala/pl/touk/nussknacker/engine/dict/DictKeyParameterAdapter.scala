package pl.touk.nussknacker.engine.dict

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.IncompatibleParameterDefinitionModification
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessNodesRewriter}
import pl.touk.nussknacker.engine.graph.expression.{DictKeyWithLabelExpression, Expression}
import pl.touk.nussknacker.engine.language.dictWithLabel.DictKeyWithLabelExpressionParser

object DictKeyParameterAdapter extends LazyLogging {

  def adaptDictKeyParameters(
      canonicalProcess: CanonicalProcess,
      parametersToAdapt: List[ParameterToAdapt],
  ): CanonicalProcess = {
    val rewriter = ProcessNodesRewriter.rewritingAllExpressions { exprIdWithMetadata => original =>
      val parameterToAdaptForExpressionOpt = parametersToAdapt.find { error =>
        error.nodeId == exprIdWithMetadata.expressionId.nodeId.id &&
        exprIdWithMetadata.parameterName.contains(error.paramName)
      }
      parameterToAdaptForExpressionOpt match {
        case Some(parameterToAdapt) =>
          logger.info(
            s"Found DictKeyWithLabel parameter [${parameterToAdapt.paramName.value}] in node [${parameterToAdapt.nodeId}] to adapt to editors [${parameterToAdapt.parameterEditors.mkString(",")}]"
          )
          adaptDictKeyExpressionToAvailableEditors(
            original,
            parameterToAdapt.parameterEditors,
            parameterToAdapt.paramName
          )(
            exprIdWithMetadata.expressionId.nodeId
          ) match {
            case Valid(modified) =>
              logger.info(
                s"Adaptation successful for parameter [${parameterToAdapt.paramName.value}] in node [${parameterToAdapt.nodeId}]: [$original] adapted to [$modified]"
              )
              modified
            case Invalid(_) =>
              logger.info(
                s"Adaptation not successful for parameter [${parameterToAdapt.paramName.value}] in node [${parameterToAdapt.nodeId}]: using original value without modification"
              )
              original
          }
        case None =>
          original
      }
    }
    rewriter.rewriteProcess(canonicalProcess)
  }

  private def adaptDictKeyExpressionToAvailableEditors(
      expression: Expression,
      editors: List[ParameterEditor],
      paramName: ParameterName,
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
    val incompatibleChangeToParameterDefinitionDetected: ValidatedNel[PartSubGraphCompilationError, Expression] =
      invalidNel(IncompatibleParameterDefinitionModification(paramName, expression.language, editors, nodeId.id))

    def spelExpressionForDictKeyWithLabelExpression(
        expression: Expression
    ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      DictKeyWithLabelExpressionParser.parseDictKeyWithLabelExpression(expression.expression) match {
        case Valid(DictKeyWithLabelExpression(key, label)) =>
          val rawValue = label.getOrElse(key)
          logger.info(
            s"Using raw value with quotes ['$rawValue'] as value of [$expression] for editors [${editors.mkString(", ")}]"
          )
          Valid(Expression.spel(s"'$rawValue'"))
        case Invalid(_) =>
          incompatibleChangeToParameterDefinitionDetected
      }
    }

    def spelExpressionForFixedList(
        expression: Expression,
        allowed: List[String]
    ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      DictKeyWithLabelExpressionParser.parseDictKeyWithLabelExpression(expression.expression) match {
        case Valid(DictKeyWithLabelExpression(key, label)) =>
          val rawValue       = label.getOrElse(key)
          val quotedRawValue = s"'$rawValue'"
          logger.info(s"${allowed.contains(rawValue)} ${allowed.contains(quotedRawValue)}")
          val condition = allowed.contains(rawValue) || allowed.contains(quotedRawValue)
          if (condition) {
            val rawValue = label.getOrElse(key)
            logger.info(
              s"Using raw value with quotes ['$rawValue'] as value of [$expression] for editors [${editors.mkString(", ")}]"
            )
            Valid(Expression.spel(s"'$rawValue'"))
          } else {
            logger.warn(
              s"Cannot use expression [$expression] for editors [${editors.mkString(", ")}], allowed values: [${allowed.mkString(", ")}]"
            )
            incompatibleChangeToParameterDefinitionDetected
          }
        case Invalid(_) =>
          incompatibleChangeToParameterDefinitionDetected
      }
    }

    def adapt(expression: Expression): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      editors.iterator
        .collectFirst {
          case SpelParameterEditor =>
            spelExpressionForDictKeyWithLabelExpression(expression)
          case SpelTemplateParameterEditor =>
            spelExpressionForDictKeyWithLabelExpression(expression)
          case FixedValuesParameterEditor(possibleValues) =>
            spelExpressionForFixedList(expression, possibleValues.map(_.expression))
          case FixedValuesWithIconParameterEditor(possibleValues) =>
            spelExpressionForFixedList(expression, possibleValues.map(_.expression))
          case FixedValuesWithRadioParameterEditor(possibleValues) =>
            spelExpressionForFixedList(expression, possibleValues.map(_.expression))
        }
        .collect { case v @ Validated.Valid(_) =>
          v
        }
        .getOrElse(incompatibleChangeToParameterDefinitionDetected)
    }
    adapt(expression)
  }

  final case class ParameterToAdapt(
      nodeId: String,
      paramName: ParameterName,
      parameterEditors: List[ParameterEditor],
  )

}
