import $ from 'jquery';
import appConfig from 'appConfig'
import _ from 'lodash'

export default {

  fetchProcesses() {
    return promiseWrap($.get(appConfig.API_URL + '/processes'))
  },

  fetchProcessDetails(processId) {
    return promiseWrap($.get(appConfig.API_URL + '/processes/' + processId))
  },

  deploy(processId) {
    return promiseWrap($.post(appConfig.API_URL + '/processManagement/deploy/' + processId))
  },

  stop(processId) {
    return promiseWrap($.post(appConfig.API_URL + '/processManagement/cancel/' + processId))
  },

  validateProcess: (process) => {
    return ajaxCall({
      url: appConfig.API_URL + '/processValidation',
      type: 'POST',
      data: JSON.stringify(process)
    });
  },

  editProcessNode: (processId, nodeId, node) => {
    return ajaxCall({
      url: appConfig.API_URL + '/processes/' + processId + '/json/node/' + nodeId,
      type: 'PUT',
      data: JSON.stringify(node)
    });
  },

  editProcessProperties(processId, nodeId, properties) {
    return ajaxCall({
      url: appConfig.API_URL + '/processes/' + processId + '/json/properties',
      type: 'PUT',
      data: JSON.stringify(properties)
    });
  },

  saveProcess(processId, processJson) {
    return ajaxCall({
      url: appConfig.API_URL + '/processes/' + processId + '/json',
      type: 'PUT',
      data: JSON.stringify(processJson)
    })
  }

}

var ajaxCall = (opts) => {
  var requestOpts = {
    headers: {
      'Content-Type': 'application/json'
    },
    ...opts
  }
  return promiseWrap($.ajax(requestOpts))
}

var promiseWrap = (plainAjaxCall) => {
  return new Promise((resolve, reject) => {
    plainAjaxCall.done(resolve).fail(reject)
  })
}

//fixme albo zmienic jak validationResult przychodzi z backendu, albo zrobic w backendzie jakos skipUnknownFields
var omitSomeProcessFields = (process) => {
  return process
}