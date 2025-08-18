package pl.touk.nussknacker.engine.spel

import org.springframework.expression.ParserContext
import pl.touk.nussknacker.engine.graph.expression.{Expression => GraphExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

sealed abstract class SpelFlavour(val languageId: Language, val parserContext: Option[ParserContext])

object SpelFlavour {

  object Standard extends SpelFlavour(GraphExpression.Language.Spel, None)

  object Template extends SpelFlavour(GraphExpression.Language.SpelTemplate, Some(ParserContext.TEMPLATE_EXPRESSION))

}
