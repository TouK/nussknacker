#Intro

Before designing your own processes first you have to define model of your data 
and means to retrieve them.  


#Defining and discovering model



#ProcessConfigCreator - entry point of model
pl.touk.esp.engine.api.process.ProcessConfigCreator

```scala
trait ProcessConfigCreator {

  def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]]

  def services(config: Config) : Map[String, WithCategories[Service]]

  def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]]

  def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]]

  def listeners(config: Config): Seq[ProcessListener]

  def exceptionHandlerFactory(config: Config) : ExceptionHandlerFactory

  def globalProcessVariables(config: Config): Map[String, WithCategories[AnyRef]]

  def buildInfo(): Map[String, String]

  def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]]

}
```

##Passing ProcessConfigCreator to Nussknacker

##Categories



#Basic blocks

##Creating sources and sinks

##Defining model classes

##Creating service
To create service one needs to extend `pl.touk.nussknacker.engine.api.Service` interface and annotate one method with
`@MethodToInvoke` and return `scala.concurrent.Future`. Annotated method will be invoked by Nussknacker for every element passing through this service.
Service can do anything - it could be HTTP call or some static mapping.

Service divides in processors and enrichers. Processor does not return anything and enricher return some value. 
So to be classified as `Processor` annotated method has to return `Future[Unit]` in other case service will be classified as `Enricher`.

Sample enricher looks like this:
```scala
class ClientService extends Service {
  @MethodToInvoke
  def invoke(@ParamName("clientId") clientId: String)
            (implicit ec: ExecutionContext): Future[Client] = {
    val clients = Map(
      "1" -> Client("1", "Alice", "123"), 
      "2" -> Client("1", "Bob", "234") 
    )
    Future { clients.get(clientId) }
  }
}
```
Every non-implicit parameter should be annotated with `@ParamName` - value from annotation will be used in UI to display parameter name.
You have full control over non-implicit parameters, on the other hand implicit parameters are injected by Nussknacker.

### Service in tests from file
Service in tests can behave as in production, but sometimes it seems wrong or impractical - for example when service sends mail. 
So Nussknacker gives you control to decide how your service behaves during tests from file.
Here's an example:

```scala
class SendMailProcessor extends Service {
  @MethodToInvoke
  def invoke(@ParamName("mailAddress") mailAddress: String)
            (implicit ex: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    Future {
      if (collector.collectorEnabled) {
        collector.collect(s"We are in test mode! Pretend to send mail to ${mailAddress}")
      } else {
        sendMail(mailAddress)
      }
    }
  }
}
```

`SendMailProcessor` uses `ServiceInvocationCollector` which is enabled in test mode. 
Collected value can be seen in UI in mocked service after tests, so for example you can use `collector.collect()` 
to collect HTTP request that you would normally send in production.

###Defining parameters

###Lazy values (use with caution)

##Exception handlers

##Common caveats

#Advanced subjects

##Global variables

##Signals

##Custom stream transformers

##Process listeners