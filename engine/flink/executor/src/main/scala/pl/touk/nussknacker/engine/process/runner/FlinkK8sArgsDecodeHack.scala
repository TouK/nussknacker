package pl.touk.nussknacker.engine.process.runner

// K8s operator parses args as command line arguments - see KubernetesApplicationClusterEntrypoint,
// ClusterEntrypointUtils.parseParametersOrExit and similar issue: https://issues.apache.org/jira/browse/FLINK-32126
// so we need to escape some characters
// This class is used by external project only
object FlinkK8sArgsDecodeHack {

  // FRH - flink replacement hack....
  def prepareProgramArgs(list: Array[String]): Array[String] = list.map(
    _.replace("__FRH_", "\"")
      .replace("__FRH2_", "\n")
      .replace("__FRH3_", "'")
      .replace("__FRH4_", "#")
  )

}
