import React from "react"
import * as VisualizationUrl from "../common/VisualizationUrl"
import * as _ from "lodash"
import * as  queryString from 'query-string'
import PeriodicallyReloadingComponent from "../components/PeriodicallyReloadingComponent"
import history from "../history"
import HttpService from "../http/HttpService"

class BaseProcesses extends PeriodicallyReloadingComponent {

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
    {label: 'Show all types processes', value: null},
    {label: 'Show only processes', value: false},
    {label: 'Show only subprocesses', value: true},
  ]

  constructor(props) {
    super(props)
    this.query = queryString.parse(this.props.history.location.search, {arrayFormat: 'comma'})
  }

  customSelectTheme(theme) {
    return {
      ...theme,
      colors: {
        ...theme.colors,
        primary: '#0e9ae0',
      }
    }
  }

  reloadProcesses(showLoader, search) {
    this.setState({showLoader: showLoader == null ? true : showLoader})

    const query = _.pick(queryString.parse(window.location.search), 'categories', 'isSubprocess')
    const data = Object.assign(query, search || {} , this.queries || {})

    HttpService.fetchProcesses(data).then (fetchedProcesses => {
      if (!this.state.showAddProcess) {
        this.setState({processes: fetchedProcesses, showLoader: false})
      }
    }).catch(() => this.setState({ showLoader: false }))
  }

  reloadStatuses() {
    HttpService.fetchProcessesStatus().then (statuses => {
      if (!this.state.showAddProcess) {
        this.setState({ statuses: statuses, showLoader: false, statusesLoaded: true })
      }
    }).catch(() => this.setState({ showLoader: false }))
  }

  retrieveSelectedCategories(categories) {
    return _.filter(this.props.filterCategories, (category) => {
      return _.find(categories || [], categoryName => categoryName === category.value)
    })
  }

  onSearchChange = (event) => {
    const params = {search: event.target.value}
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(params)})
    this.setState(params)
  }

  onSort = (sort) => {
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(sort)})
    this.setState({sort: sort})
  }

  onCategoryChange = (elements) => {
    const params = {categories: _.map(elements, 'value'), page: 0}
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(params)})
    this.setState({selectedCategories: elements, page: 0})
    this.reloadProcesses()
  }

  onIsSubprocessChange = (element) => {
    const params = {isSubprocess: element.value, page: 0}
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(params)})
    this.setState(params)
    this.reloadProcesses()
  }

  onPageChange = (page) => {
    const params = {page: page}
    this.props.history.replace({search: VisualizationUrl.setAndPreserveLocationParams(params)})
    this.setState(params)
  }

  processStatusClass = (process, statusesLoaded, statuses) => {
    const processName = process.name
    const shouldRun = process.currentlyDeployedAt.length > 0
    const statusesKnown = statusesLoaded
    const isRunning = statusesKnown && _.get(statuses[processName], 'isRunning')

    if (isRunning) {
      return "status-running"
    } else if (shouldRun) {
      return statusesKnown ? "status-notrunning" : "status-unknown"
    }

    return null
  }

  processStatusTitle = processStatusClass => {
    if (processStatusClass === "status-running") {
      return "Running"
    } else if (processStatusClass === "status-notrunning") {
      return "Not running"
    } else if (processStatusClass === "status-unknown") {
      return "Unknown state"
    }

    return null
  }

  showMetrics = (process) => {
    history.push('/metrics/' + process.name)
  }

  showProcess = (path, process) => {
    history.push(VisualizationUrl.visualizationUrl(path, process.name))
  }
}

export default BaseProcesses
