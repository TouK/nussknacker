package pl.touk.esp.engine.flink.api

import org.apache.flink.api.common.functions.RuntimeContext

trait RuntimeContextLifecycle {

  def open(runtimeContext: RuntimeContext): Unit = {}

}
