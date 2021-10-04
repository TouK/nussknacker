/* eslint-disable i18next/no-literal-string */
import {omitBy} from "lodash"
import Moment from "moment"
import * as  queryString from "query-string"
import {ParseOptions} from "query-string"
import {NodeId} from "../types"

export const visualizationBasePath = `/visualization`
export const visualizationPath = `${visualizationBasePath}/:processId`

function fromTimestampOrDate(tsOrDate) {
  const asInt = parseInt(tsOrDate)

  if (Number.isInteger(asInt) && !isNaN(tsOrDate)) {
    return Moment(asInt)
  }

  return Moment(tsOrDate)
}

export function visualizationUrl(processName: string, nodeId?: NodeId): string {
  const baseUrl = `${visualizationBasePath}/${encodeURIComponent(processName)}`
  return queryString.stringifyUrl({url: baseUrl, query: {nodeId}})
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
