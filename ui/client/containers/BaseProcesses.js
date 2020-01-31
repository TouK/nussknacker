import * as _ from "lodash"
import * as  queryString from "query-string"
import React from "react"
import ProcessStateUtils from "../components/Process/State/ProcessStateUtils"
import * as VisualizationUrl from "../common/VisualizationUrl"
import PeriodicallyReloadingComponent from "../components/PeriodicallyReloadingComponent"
import history from "../history"
import HttpService from "../http/HttpService"
import Metrics from "./Metrics"

class BaseProcesses extends PeriodicallyReloadingComponent {
  searchItems = ["categories"]
  shouldReloadStatuses = false
  intervalTime = 15000
  queries = {}
  page = ""

  filterIsSubprocessOptions = [
    {label: "Show all types processes", value: undefined},
    {label: "Show only processes", value: false},
    {label: "Show only subprocesses", value: true},
  ]

  prepareState(withoutCategories) {
    const query = queryString.parse(this.props.history.location.search, {
      arrayFormat: "comma",
      parseNumbers: true,
      parseBooleans: true,
    })

    let state = {
      statuses: null,
      processes: [],
      clashedNames: [],
      showLoader: true,
      showAddProcess: false,
      search: query.search || "",
      page: query.page || 0,
      sort: {column: query.column || "name", direction: query.direction || 1},
    }

    if (withoutCategories == null) {
      Object.assign(state, {
        selectedCategories: this.retrieveSelectedCategories(query.categories),
      })
    }

    return state
  }

  onMount() {
    this.reloadProcesses()
    if (this.shouldReloadStatuses) {
      this.reloadStatuses()
    }

    if (this.page === "processes" || this.page === "subProcesses") {
      this.loadAllClashedNames()
    }
  }

  reload() {
    this.reloadProcesses(false)

    if (this.shouldReloadStatuses) {
      this.reloadStatuses()
    }
  }

  reloadProcesses(shouldShowLoader, search) {
    this.showLoader(shouldShowLoader)

    const searchParams = this.prepareSearchParams(search)
    HttpService.fetchProcesses(searchParams).then(response => {
      if (!this.state.showAddProcess) {
        this.setState({processes: response.data, showLoader: false})
      }
    }).catch(() => this.setState({showLoader: false}))
  }

  showLoader(shouldShowLoader) {
    this.setState({showLoader: shouldShowLoader == null ? true : shouldShowLoader})
  }

  prepareSearchParams(search) {
    const query = _.pick(queryString.parse(window.location.search), this.searchItems || [])
    return Object.assign(query, search, this.queries || {})
  }

  loadAllClashedNames() {
    this.loadClashedNames(defaultProcessesSearchParams, (data) => HttpService.fetchProcesses(data))
    this.loadClashedNames(defaultSubProcessesSearchParams, (data) => HttpService.fetchProcesses(data))
    this.loadClashedNames(defaultArchivedProcessesSearchParams, (data) => HttpService.fetchProcesses(data))
    this.loadClashedNames({}, () => HttpService.fetchCustomProcesses())
  }

  loadClashedNames = (searchParams, fetch) => {
    const query = _.pick(queryString.parse(window.location.search), this.searchItems || [])
    const data = Object.assign(query, searchParams)
    fetch(data).then(response => {
      this.setState((prevState, props) => ({
        clashedNames: prevState.clashedNames.concat(response.data.map(process => process.name)),
      }))
    })
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
    this.afterElementChange({categories: _.map(elements, "value"), page: 0}, true)
  }

  onIsSubprocessChange = (element) => {
    this.afterElementChange({isSubprocess: element.value, page: 0}, true)
  }

  onDeployedChange = (element) => {
    this.afterElementChange({isDeployed: element.value, page: 0}, true)
  }

  showMetrics = (process) => () => {
    history.push(Metrics.pathForProcess(process.name))
  }

  showProcess = (process) => () => {
    history.push(VisualizationUrl.visualizationUrl(process.name))
  }

  getIntervalTime() {
    return _.get(this.props, "featuresSettings.intervalTimeSettings.processes", this.intervalTime)
  }

  getProcessState = (process) => {
    if (this.state.statuses == null) {
      return null
    }

    return _.get(this.state.statuses, process.name, null)
  }

  isRunning = (process) => ProcessStateUtils.isDeployed(process) && ProcessStateUtils.isRunning(this.getProcessState(process))
}

const defaultProcessesSearchParams = {isSubprocess: false, isArchived: false}
const defaultSubProcessesSearchParams = {isSubprocess: true}
const defaultArchivedProcessesSearchParams = {isArchived: true}

export default BaseProcesses
