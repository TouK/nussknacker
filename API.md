#Intro

Before designing your own processes first you have to define model of your data 
and means to rerieve them.  


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

###Lazy values (use with caution)


##Creating processors and enrichers

###Defining parameters

##Exception handlers

##Common caveats

#Advanced subjects

##Global variables

##Signals

##Custom stream transformers

##Process listeners