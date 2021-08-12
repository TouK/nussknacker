/* eslint-disable i18next/no-literal-string */
import {omitBy} from "lodash"
import Moment from "moment"
import * as  queryString from "query-string"
import {ParseOptions} from "query-string"
import {NodeId} from "../types"
import {BASE_PATH} from "../config";

export const visualizationBasePath = `visualization`
export const visualizationPath = `/${visualizationBasePath}/:processId`

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

export function processNodeUrl(processName: string, nodeId: NodeId) {
  const baseUrl = `${visualizationBasePath}/${encodeURIComponent(processName)}`
  const nodeIdUrlPart = nodeIdPart(nodeId)
  return BASE_PATH + baseUrl + nodeIdUrlPart
}

export function visualizationUrl(processName: string, nodeId?: NodeId, edgeId?: NodeId) {
  const baseUrl = `/${visualizationBasePath}/${encodeURIComponent(processName)}`
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

export const defaultArrayFormat: ParseOptions["arrayFormat"] = "comma"

export function normalizeParams<T extends Record<any, any>>(object: T) {
  return queryString.parse(queryString.stringify(object, {arrayFormat: defaultArrayFormat})) as Record<keyof T, string>
}

export function setAndPreserveLocationParams(params, arrayFormat = defaultArrayFormat): string {
  const queryParams = queryString.parse(window.location.search, {arrayFormat})
  const resultParams = omitBy(Object.assign({}, queryParams, params), (e) => {
    return e == null || e === "" || e === 0
  })

  return queryString.stringify(resultParams, {arrayFormat})
}
