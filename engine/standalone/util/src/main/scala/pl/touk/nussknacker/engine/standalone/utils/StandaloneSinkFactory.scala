package pl.touk.nussknacker.engine.standalone.utils

import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, MethodToInvoke}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}

import scala.concurrent.{ExecutionContext, Future}

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(): Sink = new Sink {
    override def testDataOutput: Option[Any => String] = Some(_.toString)
  }


}


//TODO: this is not so easy to use...
//probably because LazyParameter is not a monad or sth...
trait StandaloneSinkWithParameters extends Sink {

  def prepareResponse(evaluateLazyParameter: LazyParameterInterpreter): (Context, ExecutionContext) => Future[Any]

}