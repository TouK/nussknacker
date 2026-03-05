package pl.touk.nussknacker.engine.dict

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.IncompatibleParameterDefinitionModification
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessNodesRewriter}
import pl.touk.nussknacker.engine.graph.expression.{DictKeyWithLabelExpression, Expression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.language.dictWithLabel.DictKeyWithLabelExpressionParser

object DictKeyParameterAdapter extends LazyLogging {

  def adaptParameters(
      canonicalProcess: CanonicalProcess,
      parametersToAdapt: List[ParameterToAdapt],
  ): CanonicalProcess = {
    val nodeNamesById = canonicalProcess.collectAllNodes.map(node => node.id -> node.name).toMap
    val rewriter = ProcessNodesRewriter.rewritingAllExpressions { exprIdWithMetadata => original =>
      val parameterToAdaptForExpressionOpt = parametersToAdapt.find { error =>
        error.nodeId == exprIdWithMetadata.expressionId.nodeId &&
        exprIdWithMetadata.parameterName.contains(error.paramName)
      }
      parameterToAdaptForExpressionOpt match {
        case Some(parameterToAdapt) =>
          logger.info(
            s"Found parameter [${parameterToAdapt.paramName.value}] in node [${parameterToAdapt.nodeId}] that needs to be adapted to editors [${parameterToAdapt.parameterEditors.mkString(",")}]"
          )
          adaptDictKeyExpressionToAvailableEditors(
            original,
            parameterToAdapt.parameterEditors,
            parameterToAdapt.paramName
          )(
            exprIdWithMetadata.expressionId.nodeId,
            nodeNamesById
              .getOrElse(exprIdWithMetadata.expressionId.nodeId, NodeName(exprIdWithMetadata.expressionId.nodeId.value))
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
      implicit nodeId: NodeId,
      nodeName: NodeName
  ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
    val incompatibleChangeToParameterDefinitionDetected: ValidatedNel[PartSubGraphCompilationError, Expression] =
      invalidNel(IncompatibleParameterDefinitionModification(paramName, expression.language, editors, nodeId, nodeName))

    def adaptToSpel(
        expression: Expression
    ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      expression.language match {
        case Language.DictKeyWithLabel =>
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
        case Language.TabularDataDefinition =>
          incompatibleChangeToParameterDefinitionDetected
        case Language.Json =>
          incompatibleChangeToParameterDefinitionDetected
        case Language.JsonTemplate =>
          incompatibleChangeToParameterDefinitionDetected
        case Language.Spel =>
          incompatibleChangeToParameterDefinitionDetected
        case Language.SpelTemplate =>
          incompatibleChangeToParameterDefinitionDetected
      }
    }

    def adaptToOneOfAllowedValues(
        expression: Expression,
        allowed: List[String]
    ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      expression.language match {
        case Language.Spel | Language.SpelTemplate =>
          if (allowed.contains(expression.expression)) {
            logger.info(
              s"Value of expression [$expression] is valid as one of fixed values [${allowed.mkString(", ")}] for editors [${editors.mkString(", ")}]"
            )
            Valid(expression)
          } else {
            logger.warn(
              s"Value of expression [$expression] is not valid as one of fixed values [${allowed.mkString(", ")}] for editors [${editors.mkString(", ")}]"
            )
            incompatibleChangeToParameterDefinitionDetected
          }
        case Language.DictKeyWithLabel =>
          DictKeyWithLabelExpressionParser.parseDictKeyWithLabelExpression(expression.expression) match {
            case Valid(DictKeyWithLabelExpression(key, label)) =>
              val quotedKey      = s"'$key'"
              val quotedLabelOpt = label.map(l => s"'$l'")

              val matchingAllowedValue = {
                if (allowed.contains(quotedKey)) Some(quotedKey)
                else if (quotedLabelOpt.exists(allowed.contains)) quotedLabelOpt
                else None
              }

              matchingAllowedValue match {
                case Some(matchingValue) =>
                  val adapted = Expression.spel(matchingValue)
                  logger.info(
                    s"Value of expression [$expression] is adapted to [$adapted] for editors [${editors.mkString(", ")}]"
                  )
                  Valid(adapted)
                case None =>
                  logger.warn(
                    s"Value of expression [$expression] cannot be adapted for editors [${editors.mkString(", ")}]"
                  )
                  incompatibleChangeToParameterDefinitionDetected
              }
            case Invalid(_) =>
              incompatibleChangeToParameterDefinitionDetected
          }
        case Language.TabularDataDefinition =>
          incompatibleChangeToParameterDefinitionDetected
        case Language.Json =>
          incompatibleChangeToParameterDefinitionDetected
        case Language.JsonTemplate =>
          incompatibleChangeToParameterDefinitionDetected
      }
    }

    def adapt(expression: Expression): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      editors
        .collectFirst {
          case SpelParameterEditor =>
            adaptToSpel(expression)
          case SpelTemplateParameterEditor =>
            adaptToSpel(expression)
          case FixedValuesParameterEditor(possibleValues) =>
            adaptToOneOfAllowedValues(expression, possibleValues.map(_.expression))
          case FixedValuesWithIconParameterEditor(possibleValues) =>
            adaptToOneOfAllowedValues(expression, possibleValues.map(_.expression))
          case FixedValuesWithRadioParameterEditor(possibleValues) =>
            adaptToOneOfAllowedValues(expression, possibleValues.map(_.expression))
        }
        .collect { case v @ Validated.Valid(_) => v }
        .getOrElse(incompatibleChangeToParameterDefinitionDetected)
    }
    adapt(expression)
  }

  final case class ParameterToAdapt(
      nodeId: NodeId,
      paramName: ParameterName,
      parameterEditors: List[ParameterEditor],
  )

}
