package pl.touk.nussknacker.engine.spel.parser

import org.springframework.expression.Expression

case class ExpressionWithPosition(expression: Expression, start: Int, end: Int)
