This module contains code copied from Apache Flink 1.15 (https://github.com/apache/flink/tree/release-1.15/flink-scala).
We do it to be able to upgrade Scala version, as Flink cannot do it in foreseeable time. 
The package in `FlinkScalaKryoInstantiator` has to remain the same as in original, as Flink instantiates it 
with reflection, with hardcoded FQN.