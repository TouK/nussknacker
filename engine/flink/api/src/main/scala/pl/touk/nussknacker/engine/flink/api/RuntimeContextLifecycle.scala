package pl.touk.nussknacker.engine.flink.api

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.Lifecycle
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

trait FlinkEngineRuntimeContext extends EngineRuntimeContext {
  def runtimeContext: RuntimeContext
}

/*
  This trait should be used in (hopefully rare) cases when Service/Listener has to use Flink RuntimeContext directly
 */
trait RuntimeContextLifecycle extends Lifecycle {

  final override def open(context: EngineRuntimeContext): Unit = {
    openWithFlinkContext(context.asInstanceOf[FlinkEngineRuntimeContext])
  }

  def openWithFlinkContext(runtimeContext: FlinkEngineRuntimeContext): Unit = {}

}
