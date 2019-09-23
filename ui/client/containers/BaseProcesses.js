import React from "react"
import * as VisualizationUrl from "../common/VisualizationUrl"
import * as _ from "lodash"
import * as  queryString from 'query-string'
import PeriodicallyReloadingComponent from "../components/PeriodicallyReloadingComponent"
import history from "../history"
import HttpService from "../http/HttpService"
import * as ProcessStateUtils from "../common/ProcessStateUtils"
import Metrics from "./Metrics";

class BaseProcesses extends PeriodicallyReloadingComponent {
  searchItems = ['categories']
  shouldReloadStatuses = false
  intervalTime = 15000
  queries = {}

  customSelectStyles = {
    control: styles => ({
      ...styles,
      minHeight: 45,
      fontSize: 14,
      color: '#555555',
    }),
    option: styles => ({
      ...styles,
      fontSize: 14,
      color: '#555555',
    })
  }

  filterIsSubprocessOptions = [
    {label: 'Show all types processes', value: undefined},
    {label: 'Show only processes', value: false},
    {label: 'Show only subprocesses', value: true},
  ]

  customSelectTheme(theme) {
    return {
      ...theme,
      colors: {
        ...theme.colors,
        primary: '#0e9ae0',
      }
    }
  }

  prepareState(withoutCategories) {
    const query = queryString.parse(this.props.history.location.search, {
      arrayFormat: 'comma',
      parseNumbers: true,
      parseBooleans: true
    })

    let state = {
      processes: [],
      showLoader: true,
      showAddProcess: false,
      search: query.search || "",
      page: query.page || 0,
      sort: {column: query.column || "name", direction: query.direction || 1}
    }

    if (withoutCategories == null) {
      Object.assign(state, {
        selectedCategories: this.retrieveSelectedCategories(query.categories)
      })
    }

    return state
  }

  onMount() {
    this.reloadProcesses()

    if (this.shouldReloadStatuses) {
      this.reloadStatuses()
    }
  }

  reload() {
    this.reloadProcesses(false)

    if (this.shouldReloadStatuses) {
      this.reloadStatuses()
    }
  }

  reloadProcesses(showLoader, search) {
    this.setState({showLoader: showLoader == null ? true : showLoader})

    const query = _.pick(queryString.parse(window.location.search), this.searchItems || [])
    const data = Object.assign(query, search, this.queries || {})

    HttpService.fetchProcesses(data).then(response => {
      if (!this.state.showAddProcess) {
        this.setState({processes: response.data, showLoader: false})
      }
    }).catch(() => this.setState({showLoader: false}))
  }

  reloadStatuses() {
    HttpService.fetchProcessesStatus().then(response => {
      if (!this.state.showAddProcess) {
        this.setState({statuses: response.data, showLoader: false, statusesLoaded: true})
      }
    }).catch(() => this.setState({showLoader: false}))
  }

  retrieveSelectedCategories(data) {
    const categories = _.isArray(data) ? data : [data]
    return _.filter(this.props.filterCategories, (category) => {
      return _.find(categories || [], categoryName => categoryName === category.value)
    })
  }

  afterElementChange(params, reload, showLoader, search) {
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(params)})
    this.setState(params)

    if (reload) {
      this.reloadProcesses(showLoader, search)
    }
  }

  onSearchChange = (event) => {
    this.afterElementChange({search: event.target.value})
  }

  onSort = (sort) => {
    this.afterElementChange(sort)
  }

  onPageChange = (page) => {
    this.afterElementChange({page: page})
  }

  onCategoryChange = (elements) => {
    this.afterElementChange({categories: _.map(elements, 'value'), page: 0}, true)
  }

  onIsSubprocessChange = (element) => {
    this.afterElementChange({isSubprocess: element.value, page: 0}, true)
  }

  onDeployedChange = (element) => {
    this.afterElementChange({isDeployed: element.value, page: 0}, true)
  }

  showMetrics = (process) => {
    history.push(Metrics.pathForProcess(process.name))
  }

  showProcess = (process) => {
    history.push(VisualizationUrl.visualizationUrl(process.name))
  }

  processStatusClass = (process) => {
    const processName = process.name
    const shouldRun = process.currentlyDeployedAt.length > 0
    return ProcessStateUtils.getStatusClass(this.state.statuses[processName], shouldRun, this.state.statusesLoaded)
  }

  processStatusTitle = (process) => {
    const processName = process.name
    const shouldRun = process.currentlyDeployedAt.length > 0
    return ProcessStateUtils.getStatusMessage(this.state.statuses[processName], shouldRun, this.state.statusesLoaded)
  }

  getIntervalTime() {
    return _.get(this.props, "featuresSettings.intervalTimeSettings.processes", this.intervalTime)
  }

}

export default BaseProcesses