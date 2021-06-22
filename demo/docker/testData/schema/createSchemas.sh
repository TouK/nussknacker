#!/bin/bash
cd $(dirname $0)
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json"  --data @transactions-value.json http://localhost:3082/subjects/transactions-value/versions -v
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json"  --data @processedEvents-value.json http://localhost:3082/subjects/processedEvents-value/versions -v
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json"  --data @alerts-value.json http://localhost:3082/subjects/alerts-value/versions -v
