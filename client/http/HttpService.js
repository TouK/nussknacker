import $ from 'jquery';
import appConfig from 'appConfig'
import _ from 'lodash'
import NotificationSystem from 'react-notification-system';
import React from 'react'


export default {


  setNotificationSystem(ns) {
    notificationSystem = ns;
  },


  addInfo(message) {
    if (notificationSystem) {
      notificationSystem.addNotification({
        message: message,
        level: 'success',
        autoDismiss: 1
      })
    }
  },

  addError(message, error, showErrorText) {

    var details = showErrorText && error.responseText ? (<div>{error.responseText}</div>) : null;
    if (notificationSystem) {
      notificationSystem.addNotification({
        message: message,
        level: 'error',
        autoDismiss: 5,
        children: details
      })
    }
  },

  fetchLoggedUser() {
    return promiseWrap($.get(appConfig.API_URL + '/user')).then((user) => ({
      id: user.id,
      categories: user.categories,
      hasPermission(name) {
        return user.permissions.includes(name)
      },
      canRead : user.permissions.includes("Read"),
      canDeploy : user.permissions.includes("Deploy"),
      canWrite : user.permissions.includes("Write"),
    }))
  },

  fetchGrafanaSettings() {
    return promiseWrap($.get(appConfig.API_URL + '/settings/grafana'))
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
      .catch((error) => this.addError(`Cannot fetch statuses`, error) );
  },

  fetchSingleProcessStatus(processId) {
    return promiseWrap($.get(appConfig.API_URL + `/processes/${processId}/status`))
      .catch((error) => this.addError(`Cannot fetch status`, error) );

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

  validateProcess: (process) => {
    return ajaxCall({
      url: appConfig.API_URL + '/processValidation',
      type: 'POST',
      data: JSON.stringify(process)
    });
  },

  saveProcess(processId, processJson) {
    return ajaxCall({
      url: appConfig.API_URL + '/processes/' + processId + '/json',
      type: 'PUT',
      data: JSON.stringify(processJson)
    }).then(() => this.addInfo(`Process ${processId} was saved`))
      .catch((error) => this.addError(`Failed to save`, error));

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

var notificationSystem = null;

var promiseWrap = (plainAjaxCall) => {
  return new Promise((resolve, reject) => {
    plainAjaxCall.done(resolve).fail(reject)
  })
}