Project status
==============
Nussknacker is production ready - it's been used in major Polish telcom since beginning of 2017. However, it's still
under active development, so some parts of the API may change in the future.


Nussknacker versions
====================

0.7.0 
---------
- global imports in expressions
- [ITDEVESP-298] - deployment standalone on multiple nodes
- typed SpEL expressions - first iteration
- [ITDEVESP-298] - can post process standalone results
- support for java services
- [ITDEVESP-298] - handling get requests in standalone mode
- [ITDEVESP-298] - metric fixes for standalone
- compare with other env
- [ITDEVESP-295] - split in request/response mode by expression
- ProcessConfigCreator Java API support added
- [ITDEVESP-278] extendable authentication
- comparing environments - first part, can compare processes
- [ITDEVESP-280] subprocess versions
- process migrations + some refactoring
- [ITDEVESP-117] - async execution with toggle
- better exception for errors in service invocations
- nussknacker java api
- spring version bump because of SPR-9194

0.6.0
---------
First open source version :)


Compatibility matrix
====================

Table below contains versions of libraries/apps that can be used with Nussknacker 

|Nussknacker| Flink | Kafka | InfluxDB | Grafana |
|-----------|-------|-------|----------|---------|
| 0.7.0     |1.3.1  |0.9.0.1| 1.2.0    | 4.0.1   |
| 0.6.0     |1.3.1  |0.9.0.1| 1.2.0    | 4.0.1   |


