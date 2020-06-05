/* eslint-disable i18next/no-literal-string */
import * as  queryString from "query-string"
import React, {PropsWithChildren} from "react"
import * as VisualizationUrl from "../common/VisualizationUrl"
import HttpService from "../http/HttpService"
import history from "../history"
import {isArray, pick} from "lodash"
import {RootState} from "../reducers"
import {getLoggedUser, getFeatureSettings} from "../reducers/selectors/settings"
import {SearchItem, FiltersState} from "./TableFilters"

export const getProcessState = ({statuses = null}) => (process) => statuses?.[process.name] || null

export type CategoryName = string

type State = {
  showLoader: boolean,
  statusesLoaded: boolean,
  processes: $TodoType[],
  statuses: $TodoType,
  sort: { column: string, direction: number },
  page: number,
  search: string,
  categories: CategoryName[],
  isDeployed?: boolean,
  isSubprocess?: boolean,
}

export type SearchQuery = Partial<{
  page: number,
  column: string,
  direction: number,
  search: string,
  categories: CategoryName[],
  isSubprocess: boolean,
  isArchived: boolean,
  isDeployed: boolean,
}>

export type BaseProcessesOwnProps = PropsWithChildren<{
  featuresSettings: $TodoType,
  page?: string,
  searchItems?: SearchItem[],
  queries?: {},
  shouldReloadStatuses?: boolean,
  defaultState?: Partial<State>,
}>

export abstract class BaseProcesses<P = {}> extends React.Component<P & BaseProcessesOwnProps, State> {
  constructor(props) {
    super(props)
    this.state = this.prepareState()
  }

  private intervalId = null

  componentDidMount() {
    this.onMount()
    this.intervalId = setInterval(() => this.reload(), this.intervalTime)
  }

  componentWillUnmount() {
    clearInterval(this.intervalId)
  }

  private baseIntervalTime = 15000

  private get intervalTime() {
    return this.props.featuresSettings?.intervalTimeSettings?.processes || this.baseIntervalTime
  }

  protected get searchItems() {return this.props.searchItems || [SearchItem.categories] }
  private get shouldReloadStatuses() {return !!this.props.shouldReloadStatuses}

  private get defaultState() {return this.props.defaultState || {}}

  private prepareState(): State {
    const query = queryString.parse(history.location.search, {
      arrayFormat: "comma",
      parseNumbers: true,
      parseBooleans: true,
    }) as SearchQuery

    return Object.assign(this.defaultState, {
      statuses: null,
      processes: [],
      showLoader: true,
      search: query.search || "",
      page: query.page || 0,
      sort: {column: query.column || "name", direction: query.direction || 1},
      statusesLoaded: false,
      categories: isArray(query.categories) ? query.categories : [query.categories],
    })
  }

  private onMount() {
    this.reloadProcesses()
    if (this.shouldReloadStatuses) {
      this.reloadStatuses()
    }
  }

  protected reload() {
    this.reloadProcesses(false)

    if (this.shouldReloadStatuses) {
      this.reloadStatuses()
    }
  }

  protected reloadProcesses(shouldShowLoader = true) {
    const searchParams = this.prepareSearchParams()
    this.setState({showLoader: shouldShowLoader !== false})

    HttpService.fetchProcesses(searchParams).then(response => {
      this.setState({processes: response.data, showLoader: false})
    }).catch(() => this.setState({showLoader: false}))
  }

  showLoader(shouldShowLoader) {
    this.setState({showLoader: shouldShowLoader !== false})
  }

  private prepareSearchParams() {
    const {queries} = this.props
    const query = pick(queryString.parse(window.location.search) as SearchQuery, this.searchItems)
    return Object.assign(query, queries)
  }

  private reloadStatuses() {
    HttpService.fetchProcessesStates().then(response => {
      this.setState({statuses: response.data, showLoader: false, statusesLoaded: true})
    }).catch(() => this.showLoader(false))
  }

  private changeSearchParams(params: SearchQuery, reload?: boolean) {
    const search = VisualizationUrl.setAndPreserveLocationParams(params)
    history.replace({search})
    this.setState(params)

    if (reload) {
      this.reloadProcesses()
    }
  }

  onSort = (sort) => {
    this.changeSearchParams(sort)
  }

  onPageChange = (page) => {
    this.changeSearchParams({page})
  }

  render() {
    return this.props.children
  }

  protected onFilterChange = ({search, ...value}: FiltersState) => {
    if (search !== undefined) {
      this.changeSearchParams({search})
    }
    if (Object.values(value).length) {
      this.changeSearchParams({...value, page: 0}, true)
    }
  }
}

export const baseMapState = (state: RootState) => ({
  loggedUser: getLoggedUser(state),
  featuresSettings: getFeatureSettings(state),
})
