/* eslint-disable i18next/no-literal-string */
import {omitBy, uniq, without} from "lodash"
import Moment from "moment"
import * as  queryString from "query-string"
import {ParseOptions} from "query-string"
import {BASE_PATH} from "../config"
import {NodeId} from "../types"
import {ensureArray} from "./arrayUtils"

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

export function extractWindowsParams(
  append?: Partial<Record<"edgeId" | "nodeId", string | string[]>>,
  remove?: Partial<Record<"edgeId" | "nodeId", string | string[]>>,
): {edgeId: string[], nodeId: string[]} {
  const {edgeId, nodeId} = queryString.parse(window.location.search, {arrayFormat: defaultArrayFormat})
  return {
    nodeId: without(uniq(ensureArray(nodeId).concat(append?.nodeId).filter(Boolean)), ...ensureArray(remove?.nodeId)),
    edgeId: without(uniq(ensureArray(edgeId).concat(append?.edgeId).filter(Boolean)), ...ensureArray(remove?.edgeId)),
  }
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

export function setAndPreserveLocationParams<T extends Record<string, unknown>>(params: T, arrayFormat = defaultArrayFormat): string {
  const queryParams = queryString.parse(window.location.search, {arrayFormat, parseNumbers: true})
  const merged = {...queryParams, ...params}
  const resultParams = omitBy(merged, (value) => !value || value === [])

  return queryString.stringify(resultParams, {arrayFormat})
}
