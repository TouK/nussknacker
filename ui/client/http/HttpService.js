import $ from "jquery";
import { API_URL } from "../config";
import React from "react";
import FileSaver from "file-saver";
import InlinedSvgs from "../assets/icons/InlinedSvgs";

if (process.env.NODE_ENV !== 'production') {
  var user = "admin";
  $.ajaxSetup({
    headers: {
      'Authorization': "Basic " + btoa(`${user}:${user}`)
    }
  });
}

export default {

  setNotificationSystem(ns) {
    notificationSystem = ns;
    if (notificationReload) {
      clearInterval(notificationReload)
    }
    //TODO: configuration?
    notificationReload = setInterval(() => this._loadNotifications(), 10000);

  },

  _loadNotifications() {
    fetch( `${API_URL}/notifications`, {
      method: 'GET',
      credentials: 'include'
    })
    .then(response => response.json())
    .then(notifications => notifications.forEach(notification => {
      notification.type === "info" ? this.addInfo(notification.message) : this.addError(notification.message)
    }))
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

  addErrorMessage(message, error, showErrorText) {
    const details = showErrorText && error ? (<div key="details" className="details">{error}</div>) : null;
    if (notificationSystem) {
      notificationSystem.addNotification({
        message: message,
        level: 'error',
        autoDismiss: 10,
        children: [(<div className="icon" key="icon" title="" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}}/>), details]
      })
    }
  },

  addError(message, error, showErrorText) {
    console.log(error);
    this.addErrorMessage(message, error.responseText, showErrorText);
  },

  availableQueryableStates() {
    return promiseWrap($.get(`${API_URL}/queryableState/list`))
  },

  queryState(processId, queryName, key) {
    return promiseWrap($.get(`${API_URL}/queryableState/fetch`, {processId, queryName, key}))
      .catch((error) => this.addError(`Cannot fetch state`, error));
  },

  fetchBuildInfo() {
    return promiseWrap($.get(API_URL + '/app/buildInfo'))
  },

  fetchHealthCheck() {
    return promiseWrap($.get(API_URL + '/app/healthCheck'))
      .then(() => ({state: "ok"}))
      .catch((error) => ({state: "error", error: error.responseText}))
  },

  fetchSettings() {
    return promiseWrap($.get(API_URL + '/settings'))
  },

  fetchLoggedUser() {
    return promiseWrap($.get(API_URL + '/user')).then((user) => ({
      id: user.id,
      categories: user.categories,
      hasPermission(permission, category){
        let permissions = user.categoryPermissions[category] || []
        return category && permissions.includes(permission)
      },
      canRead(category){
        return this.hasPermission("Read", category)
      },
      canDeploy(category){
        return this.hasPermission("Deploy", category)
      },
      canWrite(category){
        return this.hasPermission("Write", category)
      },
      isReader: user.permissions.includes("Read"),
      isDeployer: user.permissions.includes("Deploy"),
      isWriter: user.permissions.includes("Write"),
      isAdmin: user.permissions.includes("Admin"),
    }))
  },

  fetchProcessDefinitionData(processingType, isSubprocess, subprocessVersions) {
    return ajaxCall({
      url: `${API_URL}/processDefinitionData/${processingType}?isSubprocess=${isSubprocess}`,
      type: 'POST',
      data: JSON.stringify(subprocessVersions)
    }).catch((error) => {
      this.addError(`Cannot find chosen versions`, error, true)
      return Promise.reject(error)
    });
  },

  fetchComponentIds() {
    return promiseWrap($.get(`${API_URL}/processDefinitionData/componentIds`))
  },

  fetchServices() {
      return promiseWrap($.get(`${API_URL}/processDefinitionData/services`))
  },

  fetchUnusedComponents() {
    return promiseWrap($.get(`${API_URL}/app/unusedComponents`))
  },

  fetchProcessesDetails() {
    return promiseWrap($.get(API_URL + '/processesDetails'))
  },

  fetchProcesses() {
    return promiseWrap($.get(API_URL + '/processes'))
  },

  fetchCustomProcesses() {
    return promiseWrap($.get(API_URL + '/customProcesses'))
  },

  fetchSubProcesses() {
    return promiseWrap($.get(API_URL + '/subProcesses'))
  },

  fetchArchivedProcesses() {
    return promiseWrap($.get(`${API_URL}/archive`))
  },

  fetchSubProcessesDetails() {
    return promiseWrap($.get(API_URL + '/subProcessesDetails'))
  },

  fetchProcessDetails(processId, versionId, businessView) {
    const queryParams = this.businessViewQueryParams(businessView)
    return versionId ?
      promiseWrap($.get(API_URL + '/processes/' + processId + '/' + versionId, queryParams)) :
      promiseWrap($.get(API_URL + '/processes/' + processId, queryParams))
  },

  fetchProcessesStatus() {
    return promiseWrap($.get(API_URL + '/processes/status'))
      .catch((error) => this.addError(`Cannot fetch statuses`, error));
  },

  fetchSingleProcessStatus(processId) {
    return promiseWrap($.get(API_URL + `/processes/${processId}/status`))
      .catch((error) => this.addError(`Cannot fetch status`, error));

  },

  deploy(processId, comment) {
    return fetch(API_URL + '/processManagement/deploy/' + processId, {
      method: 'POST',
      body: comment,
      credentials: 'include'
    }).then(response => {
      if (!response.ok) {
        throw Error(response.statusText)
      } else {
        return response
      }
    }).then(() => {
      this.addInfo(`Process ${processId} was deployed`)
      return { isSuccess: true }
    }).catch((error) => {
      this.addError(`Failed to deploy ${processId}`, error, true)
      return { isSuccess: false }
    });
  },
//TODO: separate reusable invocation.
  invokeService(processingType, serviceName, parameters) {
    return fetch(`${API_URL}/service/${processingType}/${serviceName}`,
      {
        method: 'POST',
        body: JSON.stringify(parameters),
        credentials: 'include',
        headers: new Headers({
          'Content-Type': 'application/json'
        })
      }
    )
  },

  stop(processId, comment) {
    return fetch(API_URL + '/processManagement/cancel/' + processId,
        {
              method: 'POST',
              body: comment,
              credentials: 'include'
        }
      )
      .then(() => this.addInfo(`Process ${processId} was stopped`))
      .catch((error) => this.addError(`Failed to stop ${processId}`, error, true));
  },

  fetchProcessActivity(processId) {
    return promiseWrap($.get(`${API_URL}/processes/${processId}/activity`))
  },

  addComment(processId, versionId, comment) {
    return ajaxCall({
      url: `${API_URL}/processes/${processId}/${versionId}/activity/comments`,
      type: 'POST',
      data: comment
    }).then(() => this.addInfo(`Comment added`))
      .catch((error) => this.addError(`Failed to add comment`, error));
  },

  deleteComment(processId, commentId) {
    return ajaxCall({
      url: `${API_URL}/processes/${processId}/activity/comments/${commentId}`,
      type: 'DELETE'
    }).then(() => this.addInfo(`Comment deleted`))
      .catch((error) => this.addError(`Failed to delete comment`, error));
  },

  addAttachment(processId, versionId, file) {
    var formData = new FormData();
    formData.append("attachment", file)

    return ajaxCallWithoutContentType({
      url: `${API_URL}/processes/${processId}/${versionId}/activity/attachments`,
      type: 'POST',
      processData: false,
      contentType: false,
      data: formData
    }).then(() => this.addInfo(`Attachment added`))
      .catch((error) => this.addError(`Failed to add attachment`, error));
  },

  downloadAttachment(processId, processVersionId, attachmentId) {
    window.open(`${API_URL}/processes/${processId}/${processVersionId}/activity/attachments/${attachmentId}`)
  },

  changeProcessName(processName, newProcessName) {
    if (!_.isEmpty(newProcessName)) {
      return ajaxCall({
        url: `${API_URL}/processes/${processName}/rename/${newProcessName}`,
        type: 'PUT'
      }).then(
        () => { this.addInfo("Process name changed"); return true },
        (error) => { this.addError("Failed to change process name:", error, true); return false; }
      );
    } else {
      this.addErrorMessage("Failed to change process name:", "Name cannot be empty", true);
      return Promise.resolve(false);
    }
  },

  exportProcess(process, versionId) {
    const url = `${API_URL}/processesExport`
    fetch(url,
      {
          method: 'POST',
          body: JSON.stringify(process),
          credentials: 'include',
          headers: new Headers({
              'Content-Type': 'application/json'
          })
      }
    ).then((response) => response.blob()).then((blob) => {
      FileSaver.saveAs(blob, `${process.id}-${versionId}.json`);
    }).catch((error) => this.addError(`Failed to export`, error));
  },

  exportProcessToPdf(processId, versionId, data, businessView) {
    const url = `${API_URL}/processesExport/pdf/${processId}/${versionId}`
    const queryParams = this.businessViewQueryParams(businessView)
    fetch(queryParams ? `${url}?${queryParams}` : url,
      {
          method: 'POST',
          body: data,
          credentials: 'include'
      }
    ).then((response) => response.blob()).then((blob) => {
      FileSaver.saveAs(blob, `${processId}-${versionId}.pdf`);
    }).catch((error) => this.addError(`Failed to export`, error));
  },

  validateProcess(process) {
    return ajaxCall({
      url: API_URL + '/processValidation',
      type: 'POST',
      data: JSON.stringify(process)
    }).catch(error => {
      this.addError(`Fatal validation error, cannot save`, error, true)
      return Promise.reject(error)
    })
  },

  getTestCapabilities(process) {
    return ajaxCall({
      url: API_URL + '/testInfo/capabilities',
      type: 'POST',
      data: JSON.stringify(process)
    });
  },

  generateTestData(processId, testSampleSize, processJson) {
    return fetch(`${API_URL}/testInfo/generate/${testSampleSize}`,
      {
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

  fetchProcessCounts(processId, dateFrom, dateTo) {
    return ajaxCall({
      url: API_URL + '/processCounts/' + processId,
      type: 'GET',
      data: { dateFrom: dateFrom, dateTo: dateTo }
    }).catch(error => {
      this.addError(`Cannot fetch process counts`, error, true);
      return Promise.reject(error)
    })
  },

  saveProcess(processId, processJson, comment) {
    const processToSave = {process: processJson, comment: comment}
    return ajaxCall({
      url: `${API_URL}/processes/${processId}`,
      type: 'PUT',
      data: JSON.stringify(processToSave)
    })
      .then(() => this.addInfo(`Process ${processId} was saved`))
      .catch((error) => {
        this.addError(`Failed to save`, error, true);
        return Promise.reject(error)
      });
  },

  archiveProcess(processId) {
    return ajaxCall({
      url: `${API_URL}/archive/${processId}`,
      type: 'POST',
      data: JSON.stringify({isArchived:true})
    })
  },
  createProcess(processId, processCategory, callback, isSubprocess) {
    return ajaxCall({
      url: `${API_URL}/processes/${processId}/${processCategory}?isSubprocess=${isSubprocess}`,
      type: 'POST'
    }).then(callback, (error) => {
      this.addError(`Failed to create process:`, error, true);
    })
  },

  importProcess(processId, file, callback, errorCallback) {
    var formData = new FormData();
    formData.append("process", file)

    return ajaxCallWithoutContentType({
      url: API_URL + '/processes/import/' + processId,
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

  testProcess(processId, file, processJson, callback, errorCallback) {
    var formData = new FormData();
    formData.append("testData", file)
    formData.append("processJson", new Blob([JSON.stringify(processJson)], {type : 'application/json'}))

    return ajaxCallWithoutContentType({
      url: API_URL + '/processManagement/test/' + processId,
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
  },

  compareProcesses(processId, thisVersion, otherVersion, businessView, remoteEnv) {
    const queryParams = this.businessViewQueryParams(businessView)

    const path = remoteEnv ? 'remoteEnvironment' : 'processes'
    return ajaxCall({
      url: `${API_URL}/${path}/${processId}/${thisVersion}/compare/${otherVersion}`,
      type: 'GET',
      data: queryParams
    }).catch(error => {
      this.addError(`Cannot compare processes`, error, true);
      return Promise.reject(error)
    })
  },

  fetchRemoteVersions(processId) {
    return ajaxCall({
      url: `${API_URL}/remoteEnvironment/${processId}/versions`,
      type: 'GET',
    }).catch((error) => this.addError(`Failed to get versions from second environment`, error));
  },

  migrateProcess(processId, versionId) {
    return ajaxCall({
      url: `${API_URL}/remoteEnvironment/${processId}/${versionId}/migrate`,
      type: 'POST',
    })
      .then(() => this.addInfo(`Process ${processId} was migrated`))
      .catch((error) => this.addError(`Failed to migrate`, error, true));
  },

  fetchSignals() {
    return ajaxCall({
      url: `${API_URL}/signal`,
      type: 'GET',
    }).catch((error) => this.addError(`Failed to fetch signals`, error));
  },

  sendSignal(signalType, processId, params) {
    return ajaxCall({
      url: `${API_URL}/signal/${signalType}/${processId}`,
      type: 'POST',
      data: JSON.stringify(params)
    }).then(() => this.addInfo(`Signal send`))
      .catch((error) => this.addError(`Failed to send signal`, error));
  },

  businessViewQueryParams(businessView) {
    return businessView ? $.param({businessView}) : {}
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

var notificationReload = null;

var promiseWrap = (plainAjaxCall) => {
  return new Promise((resolve, reject) => {
    plainAjaxCall.done(resolve).fail(reject)
  })
}
