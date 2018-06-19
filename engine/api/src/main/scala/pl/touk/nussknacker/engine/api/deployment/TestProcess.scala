package pl.touk.nussknacker.engine.api.deployment

import java.nio.charset.StandardCharsets

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

object TestProcess {

  case class TestData(testData: Array[Byte])

  object TestData {
    def apply(s: String): TestData = new TestData(s.getBytes(StandardCharsets.UTF_8))
  }

  case class TestResults[T](nodeResults: Map[String, List[NodeResult[T]]],
                         invocationResults: Map[String, List[ExpressionInvocationResult[T]]],
                         mockedResults: Map[String, List[MockedResult[T]]],
                         exceptions: List[ExceptionResult[T]], variableEncoder: Any => T) {
    
    def updateNodeResult(nodeId: String, context: Context) = {
      copy(nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ NodeResult(toResult(context)))))
    }

    def updateExpressionResult(nodeId: String, context: Context, name: String, result: Any) = {
      val invocationResult = ExpressionInvocationResult(toResult(context), name, variableEncoder(result))
      copy(invocationResults = invocationResults + (nodeId -> addResults(invocationResult, invocationResults.getOrElse(nodeId, List()))))
    }

    def updateMockedResult(nodeId: String, context: Context, name: String, result: Any) = {
      val mockedResult = MockedResult(toResult(context), name, variableEncoder(result))
      copy(mockedResults = mockedResults + (nodeId -> (mockedResults.getOrElse(nodeId, List()) :+ mockedResult)))
    }

    def updateExceptionResult(espExceptionInfo: EspExceptionInfo[_ <: Throwable]) = {
      copy(exceptions = exceptions :+ ExceptionResult(toResult(espExceptionInfo.context), espExceptionInfo.nodeId, espExceptionInfo.throwable))
    }

    //when evaluating e.g. keyBy expression can be invoked more than once...
    //TODO: is it the best way to handle it??
    private def addResults(invocationResult: ExpressionInvocationResult[T], resultsSoFar: List[ExpressionInvocationResult[T]])
    = resultsSoFar.filterNot(res => res.context.id == invocationResult.context.id && res.name == invocationResult.name) :+ invocationResult

    private def toResult(context: Context): ResultContext[T] = ResultContext(context.id, context.variables.map { case (k, v) => k -> variableEncoder(v) })


  }

  case class NodeResult[+T](context: ResultContext[T])

  case class ExpressionInvocationResult[+T](context: ResultContext[T], name: String, value: T)

  case class MockedResult[+T](context: ResultContext[T], name: String, value: T)

  case class ExceptionResult[+T](context: ResultContext[T], nodeId: Option[String], throwable: Throwable)

  case class ResultContext[+T](id: String,  variables: Map[String, T])

}
