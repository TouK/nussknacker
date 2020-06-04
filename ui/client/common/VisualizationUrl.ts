/* eslint-disable i18next/no-literal-string */
import {omitBy} from "lodash"
import Moment from "moment"
import * as  queryString from "query-string"
import {nkPath} from "../config"
import {NodeId} from "../types"

export const visualizationBasePath = `${nkPath}/visualization`
export const visualizationPath = `${visualizationBasePath  }/:processId`

function nodeIdPart(nodeId): string {
  return `?nodeId=${encodeURIComponent(nodeId)}`
}

function edgeIdPart(edgeId): string {
  return `?edgeId=${encodeURIComponent(edgeId)}`
}

function fromTimestampOrDate(tsOrDate) {
  const asInt = parseInt(tsOrDate)

  if (Number.isInteger(asInt) && !isNaN(tsOrDate)) {
    return Moment(asInt)
  }

  return Moment(tsOrDate)
}

export function visualizationUrl(processName: string, nodeId?: NodeId, edgeId?: NodeId) {
  const baseUrl = `${visualizationBasePath}/${encodeURIComponent(processName)}`
  const nodeIdUrlPart = nodeId && edgeId == null ? nodeIdPart(nodeId) : ""
  const edgeIdUrlPart = edgeId && nodeId == null ? edgeIdPart(edgeId) : ""
  return baseUrl + nodeIdUrlPart + edgeIdUrlPart
}

export function extractVisualizationParams(search) {
  const queryParams = queryString.parse(search)
  const nodeId = queryParams.nodeId
  const edgeId = queryParams.edgeId
  return {nodeId, edgeId}
}

export function extractBusinessViewParams(search) {
  const queryParams = queryString.parse(search, {parseBooleans: true})
  return queryParams.businessView || false
}

export function extractCountParams(queryParams) {
  if (queryParams.from || queryParams.to) {
    const from = queryParams.from ? fromTimestampOrDate(queryParams.from) : null
    const to = queryParams.to ? fromTimestampOrDate(queryParams.to) : Moment()
    return {from, to}
  }

  return null
}

export function setAndPreserveLocationParams(params): string {
  const queryParams = queryString.parse(window.location.search, {arrayFormat: "comma"})
  const resultParams = omitBy(Object.assign({}, queryParams, params), (e) => {
    return e == null || e === "" || e === 0
  })

  return queryString.stringify(resultParams, {arrayFormat: "comma"})
}
