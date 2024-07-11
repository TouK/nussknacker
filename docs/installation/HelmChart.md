# Kubernetes - Helm chart

We provide [Helm chart](https://artifacthub.io/packages/helm/touk/nussknacker) with basic Nussknacker setup, including:

- Kafka - required only in streaming processing mode
- Grafana + InfluxDB
- One of the available engines: Flink or Lite.

Please note that Kafka (and Flink if chosen) are installed in basic configuration - for serious production deployments you probably
want to customize those to meet your needs.

You can check example usage at [Nussknacker Quickstart repository](https://github.com/TouK/nussknacker-quickstart) in `k8s-helm` directory.
