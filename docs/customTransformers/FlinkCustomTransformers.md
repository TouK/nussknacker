Flink custom transformers
=========================

Nussknacker basic building blocks like filters, enrichers and processors serve stateless streams well, but
to use Flink stateful features we have to create more complex elements. 

We provide generic aggregations in `pl.touk.nussknacker.engine.flink.util.transformer` package in nusssknacker-flink-util subproject, 
you can use most of them with `genericAssembly.jar`


