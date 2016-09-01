import $ from 'jquery';
import appConfig from 'appConfig'

export default {

  fetchProcesses(callback) {
    $.get(appConfig.API_URL + '/processes', callback)
  },

  fetchProcessDetails(processId, callback) {
    $.get(appConfig.API_URL + '/processes/' + processId, callback)
  },

  fetchProcessJson(processId, callback) {
    $.get(appConfig.API_URL + '/processes/' + processId + '/json', callback)
  },

  deploy(processId, callback) {
    $.post(appConfig.API_URL + '/processManagement/deploy/' + processId, callback)
  },

  stop(processId, callback) {
    $.post(appConfig.API_URL + '/processManagement/cancel/' + processId, callback)
  }

}