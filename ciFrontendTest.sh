#!/usr/bin/env bash

set -e

cd ui/client
npm ci
npm test
npm run build