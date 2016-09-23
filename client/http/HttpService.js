import $ from 'jquery';
import appConfig from 'appConfig'

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

  editProcessNode: (processId, nodeId, node) => {
    return _editProcessNode(processId, appConfig.API_URL + '/processes/' + processId + '/json/node/' + nodeId, node);
  },

  editProcessProperties(processId, nodeId, properties) {
    return _editProcessNode(processId, appConfig.API_URL + '/processes/' + processId + '/json/properties', properties)
  }

}

var _editProcessNode = (processId, url, node) => {
  return promiseWrap(
    $.ajax({
      url: url,
      headers: {
        'Content-Type': 'application/json'
      },
      type: 'PUT',
      data: JSON.stringify(node),
      dataType: 'json'
    })
  )
}

var promiseWrap = (plainAjaxCall) => {
  return new Promise((resolve, reject) => {
    plainAjaxCall.done(resolve).fail(reject)
  })
}