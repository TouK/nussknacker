import _ from "lodash";
import Moment from "moment";

export const visualizationRouterPath = '/visualization/:processId'

export function visualizationUrl(processName, nodeId, edgeId) {
  if (!_.isEmpty(nodeId) && !_.isEmpty(edgeId)) {
    throw new Error("cannot visualize both nodeId and edgeId")
  }
  const baseUrl = `/visualization/${encodeURIComponent(processName)}`
  const nodeIdPart = nodeId ? this.nodeIdPart(nodeId) : ""
  const edgeIdPart = edgeId ? this.edgeIdPart(edgeId) : ""
  return baseUrl + nodeIdPart + edgeIdPart
}

export function nodeIdPart(nodeId) {
  return `?nodeId=${encodeURIComponent(nodeId)}`
}

export function edgeIdPart(edgeId) {
  return `?edgeId=${encodeURIComponent(edgeId)}`
}

export function extractVisualizationParams(queryParams) {
  const urlNodeId = queryParams.nodeId;
  const urlEdgeId = queryParams.edgeId;
  return {urlNodeId, urlEdgeId}
}

export function extractBusinessViewParams(queryParams) {
  if (!_.isEmpty(queryParams.businessView)) {
    return (queryParams.businessView.toLowerCase() === "true")
  }
}

export function extractCountParams(queryParams) {
  if (!_.isEmpty(queryParams.from) || !_.isEmpty(queryParams.to)) {
    const from = queryParams.from ? fromTimestampOrDate(queryParams.from) : null;
    const to = queryParams.to ? fromTimestampOrDate(queryParams.to) : Moment();
    return {from, to};
  } else {
    return null
  }
}

function fromTimestampOrDate(tsOrDate) {
  const asInt = parseInt(tsOrDate);
  if (Number.isInteger(asInt) && !isNaN(tsOrDate))
    return Moment(asInt);
  else
    return Moment(tsOrDate);
}

export function getCurrentLocationParams() {
  var result = {}, tmp = []
  location.search
      .substr(1)
      .split("&")
      .forEach(function(item) {
        tmp = item.split("=");
        if (tmp[0] != null && tmp[0] !== "") {
          result[tmp[0]] = decodeURIComponent(tmp[1])
        }
      });
  return result
}

export function setAndPreserveLocationParams(params){
  var result = "", tmp = getCurrentLocationParams();
  Object.keys(params).forEach(function(paramName){
    if (params[paramName] == null ||  params[paramName] === "") {
      delete tmp[paramName]
    } else {
      tmp[paramName] = params[paramName]
    }
  })
  if (Object.keys(tmp).length > 0) {
    Object.keys(tmp).forEach(function(parameterName){
      result = result + "&" + parameterName + "=" + encodeURIComponent(tmp[parameterName])
    })
    result = "?" + result.substr(1)
  }
  return result
}