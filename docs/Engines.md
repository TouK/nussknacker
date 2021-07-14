Engines
=======
Nussknacker was created as GUI for Flink. However, it's also possible to use it to connect to other runtimes. 
```ProcessManager``` and ```ProcessManagerProvider``` interfaces were created to facilitate this. 
In particular, we provide experimental standalone engine, which allows using Nussknacker as method of creating REST APIs

To create/customize Nussknacker engine you have to:
- Implement ```ProcessManagerProvider``` interface and register it with ServiceLoader mechanism. 
    Each provider should have unique ```name```.
- Put implementation with all needed libraries on Nussknacker classpath. 
- Configure appropriate model to use your process manager.

To configure process category to use particular engine, see following configuration:
```
    scenarioTypes {
      "streaming": {
        type: ... //name of engine, e.g. "flinkStreaming"
        //... - additional engine config (e.g. location of Flink cluster, etc.)
        modelConfig: {
          //model configuration
          classPath: [...]
          
        }
      }
    }
```

Flink
=====
Main processing engine for Nussknacker. It's published in ```nussknacker-flink-manager``` module.
Name of engine is ```flinkStreaming```

Standalone
==========

This engine is considered **experimental**. It allows to use Nussknacker to expose REST APIs or use it as
embedded rules engine. We provide simplistic implementation of process manager in ```nussknacker-standalone-app```
module. Name of engine is ```requestResponseStandalone``` 

