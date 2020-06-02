package pl.touk.nussknacker.engine.api


/*
  Currently handling Lifecycle is supported in following cases:
  - Service
  - EspExceptionHandler
  - ProcessListener
  Please note that extending this trait in e.g. Sources, Sinks or CustomTransformers *won't* work. 
 */
trait Lifecycle {

  def open(jobData: JobData): Unit = {

  }

  def close(): Unit = {

  }

}
