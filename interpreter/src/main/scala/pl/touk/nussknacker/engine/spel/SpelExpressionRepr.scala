package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.api.Context

/*
  This is *very experimental* feature which enables access to expression AST in e.g. source or service. This can
  be e.g. used to transform spel expression to some query language
 */
case class SpelExpressionRepr(parsed: org.springframework.expression.Expression,
                              context: Context,
                              globals: Map[String, Any],
                              original: String)
