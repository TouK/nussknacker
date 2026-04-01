---
title: Install Nussknacker on Kubernetes
description: Deploy Nussknacker to Kubernetes with Helm. Use official charts to configure, install, and manage Nussknacker in a cloud environment.
sidebar_label: Helm Chart
---

# Kubernetes - Helm chart

We provide [Helm chart](https://artifacthub.io/packages/helm/touk/nussknacker) with basic Nussknacker setup, including:

- Kafka - required only in streaming processing mode
- Grafana + InfluxDB
- One of the available engines: Flink or Lite.

:::note
Kafka (and Flink if chosen) are installed in basic configuration - for production deployments you probably
want to customize those to meet your needs.
:::
