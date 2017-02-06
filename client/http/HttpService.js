import $ from "jquery";
import appConfig from "appConfig";
import React from "react";
import FileSaver from "file-saver";
import InlinedSvgs from "../assets/icons/InlinedSvgs";


export default {


  setNotificationSystem(ns) {
    notificationSystem = ns;
  },


  addInfo(message) {
    if (notificationSystem) {
      notificationSystem.addNotification({
        message: message,
        level: 'success',
        children: (<div className="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsInfo}} />),
        autoDismiss: 5
      })
    }
  },

  addError(message, error, showErrorText) {
    console.log(error)
    var details = showErrorText && error.responseText ? (<div key="details" className="details">{error.responseText}</div>) : null;
    if (notificationSystem) {
      notificationSystem.addNotification({
        message: message,
        level: 'error',
        autoDismiss: 10,
        children: [(<div className="icon" key="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>), details]
      })
    }
  },

  fetchBuildInfo() {
    return promiseWrap($.get(appConfig.API_URL + '/app/buildInfo'))
  },

  fetchHealthCheck() {
    return promiseWrap($.get(appConfig.API_URL + '/app/healthCheck'))
      .then(() => ({state: "ok"}))
      .catch((error) => ({state: "error", error: error.responseText}))
  },

  fetchLoggedUser() {
    return promiseWrap($.get(appConfig.API_URL + '/user')).then((user) => ({
      id: user.id,
      categories: user.categories,
      hasPermission(name) {
        return user.permissions.includes(name)
      },
      canRead: user.permissions.includes("Read"),
      canDeploy: user.permissions.includes("Deploy"),
      canWrite: user.permissions.includes("Write"),
    }))
  },

  fetchGrafanaSettings() {
    return promiseWrap($.get(appConfig.API_URL + '/settings/grafana'))
  },

  fetchKibanaSettings() {
    return promiseWrap($.get(appConfig.API_URL + '/settings/kibana'))
  },

  fetchProcessDefinitionData() {
    return promiseWrap($.get(appConfig.API_URL + '/processDefinitionData'))
  },

  fetchProcesses() {
    return promiseWrap($.get(appConfig.API_URL + '/processes'))
  },

  fetchProcessDetails(processId, versionId) {
    return versionId ?
      promiseWrap($.get(appConfig.API_URL + '/processes/' + processId + '/' + versionId)) :
      promiseWrap($.get(appConfig.API_URL + '/processes/' + processId))
  },

  fetchProcessesStatus() {
    return promiseWrap($.get(appConfig.API_URL + '/processes/status'))
      .catch((error) => this.addError(`Cannot fetch statuses`, error));
  },

  fetchSingleProcessStatus(processId) {
    return promiseWrap($.get(appConfig.API_URL + `/processes/${processId}/status`))
      .catch((error) => this.addError(`Cannot fetch status`, error));

  },

  deploy(processId) {
    return promiseWrap($.post(appConfig.API_URL + '/processManagement/deploy/' + processId))
      .then(() => this.addInfo(`Process ${processId} was deployed`))
      .catch((error) => this.addError(`Failed to deploy ${processId}`, error, true));
  },

  stop(processId) {
    return promiseWrap($.post(appConfig.API_URL + '/processManagement/cancel/' + processId))
      .then(() => this.addInfo(`Process ${processId} was stopped`))
      .catch((error) => this.addError(`Failed to stop ${processId}`, error, true));
  },

  fetchProcessActivity(processId) {
    return promiseWrap($.get(`${appConfig.API_URL}/processes/${processId}/activity`))
  },

  addComment(processId, versionId, comment) {
    return ajaxCall({
      url: `${appConfig.API_URL}/processes/${processId}/${versionId}/activity/comments`,
      type: 'POST',
      data: comment
    }).then(() => this.addInfo(`Comment added`))
      .catch((error) => this.addError(`Failed to add comment`, error));
  },

  addAttachment(processId, versionId, file) {
    var formData = new FormData();
    formData.append("attachment", file)

    return ajaxCallWithoutContentType({
      url: `${appConfig.API_URL}/processes/${processId}/${versionId}/activity/attachments`,
      type: 'POST',
      processData: false,
      contentType: false,
      data: formData
    }).then(() => this.addInfo(`Attachment added`))
      .catch((error) => this.addError(`Failed to add attachment`, error));
  },

  downloadAttachment(processId, processVersionId, attachmentId) {
    window.open(`${appConfig.API_URL}/processes/${processId}/${processVersionId}/activity/attachments/${attachmentId}`)
  },

  exportProcess(processId, versionId) {
    window.open(`${appConfig.API_URL}/processes/export/${processId}/${versionId}`);
  },

  exportProcessToPdf(processId, versionId, data) {
    fetch(`${appConfig.API_URL}/processes/exportToPdf/${processId}/${versionId}`,
      {
          mode: 'cors-with-forced-prefligh',
          method: 'POST',
          body: data,
          credentials: 'include'
      }
    ).then((response) => response.blob()).then((blob) => {
      FileSaver.saveAs(blob, `${processId}-${versionId}.pdf`);
    }).catch((error) => this.addError(`Failed to export`, error));
  },

  validateProcess: (process) => {
    return ajaxCall({
      url: appConfig.API_URL + '/processValidation',
      type: 'POST',
      data: JSON.stringify(process)
    });
  },

  getTestCapabilities: (process) => {
    return ajaxCall({
      url: appConfig.API_URL + '/testInfo/capabilities',
      type: 'POST',
      data: JSON.stringify(process)
    });
  },

  generateTestData: (processId, processJson) => {
    fetch(`${appConfig.API_URL}/testInfo/generate`,
      {
          mode: 'cors-with-forced-prefligh',
          method: 'POST',
          body: JSON.stringify(processJson),
          credentials: 'include',
          headers: new Headers({
        		'Content-Type': 'application/json'
          })
      }
    ).then((response) => response.blob()).then((blob) => {
      FileSaver.saveAs(blob, `${processId}-testData`);
    }).catch((error) => this.addError(`Failed to generate test data`, error));
  },

  saveProcess(processId, processJson) {
    return ajaxCall({
      url: appConfig.API_URL + '/processes/' + processId + '/json',
      type: 'PUT',
      data: JSON.stringify(processJson)
    }).then(() => this.addInfo(`Process ${processId} was saved`))
      .catch((error) => this.addError(`Failed to save`, error));

  },

  createProcess(processId, processCategory, callback) {
    return ajaxCall({
      url: `${appConfig.API_URL}/processes/${processId}/${processCategory}`,
      type: 'POST'
    }).then(callback, (error) => {
      this.addError(`Failed to create process:`, error, true);
    })
  },

  importProcess(processId, file, callback, errorCallback) {
    var formData = new FormData();
    formData.append("process", file)

    return ajaxCallWithoutContentType({
      url: appConfig.API_URL + '/processes/import/' + processId,
      type: 'POST',
      processData: false,
      contentType: false,
      data: formData
    }).then(callback, (error) => {
      this.addError(`Failed to import`, error, true);
      if (errorCallback) {
        errorCallback(error)
      }
    });
  },

  testProcess(processId, file, callback, errorCallback) {
    var formData = new FormData();
    formData.append("testData", file)

    return ajaxCallWithoutContentType({
      url: appConfig.API_URL + '/processManagement/test/' + processId,
      type: 'POST',
      processData: false,
      contentType: false,
      data: formData
    }).then(callback, (error) => {
      this.addError(`Failed to test`, error, true);
      if (errorCallback) {
        errorCallback(error)
      }
    });
  }


}


var ajaxCall = (opts) => {
  var requestOpts = {
    headers: {
      'Content-Type': 'application/json'
    },
    ...opts
  }
  return ajaxCallWithoutContentType(requestOpts)
}

var ajaxCallWithoutContentType = (opts) => promiseWrap($.ajax(opts))

var notificationSystem = null;

var promiseWrap = (plainAjaxCall) => {
  return new Promise((resolve, reject) => {
    plainAjaxCall.done(resolve).fail(reject)
  })
}