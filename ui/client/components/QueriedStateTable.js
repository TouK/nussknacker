import _ from "lodash"
import Moment from "moment"
import React from "react"
import JSONTree from "react-json-tree"
import {connect} from "react-redux"
import {Table} from "reactable"
import ActionsUtils from "../actions/ActionsUtils"
import {DATE_FORMAT} from "../config"
import HttpService from "../http/HttpService"
import {ButtonWithFocus, InputWithFocus, SelectWithFocus} from "./withFocus"
import {useTranslation} from "react-i18next"

class QueriedStateTable extends React.Component {

  constructor(props) {
    super(props)
    this.state = {
      initialState: {},
      key: "",
    }
  }

  componentDidMount() {
    this.props.actions.fetchAvailableQueryStates()
  }

  render() {
    const {t} = useTranslation()

    const queryFormRender = () => {
      let queryButtonTooltip
      if (_.isEmpty(this.selectedQueryName())) {
        queryButtonTooltip = t("queriedState.notSelected.tooltip.query", "Query name is not selected")
      } else if (_.isEmpty(this.selectedProcessId())) {
        queryButtonTooltip = t("queriedState.notSelected.tooltip.scenario", "Scenario is not selected")
      }
      return (
        <div>
          <div className="esp-form-row">
            <p>{t("queriedState.title.queryName", "Query name")}</p>
            <SelectWithFocus
              value={this.selectedQueryName()}
              onChange={(e) => this.setState({queryName: e.target.value, processId: this.processesForQueryName(e.target.value)[0]})}
            >
              {_.keys(this.props.availableQueryableStates).map((queryName, index) => (
                <option key={index} value={queryName}>{queryName}</option>))}
            </SelectWithFocus>
          </div>
          <div className="esp-form-row">
            <p>{t("queriedState.title.scenarioName", "Scenario name")}</p>
            <SelectWithFocus value={this.selectedProcessId()} onChange={(e) => this.setState({processId: e.target.value})}>
              {this.processesForQueryName(this.selectedQueryName()).map((processId, index) => (
                <option key={index} value={processId}>{processId}</option>))}
            </SelectWithFocus>
          </div>
          <div className="esp-form-row">
            <p>{t("queriedState.title.key", "Key (optional)")}</p>
            <p>Key (optional)</p>
            <InputWithFocus value={this.state.key} onChange={(e) => this.setState({key: e.target.value})}/>
            <ButtonWithFocus
              type="button"
              className="modalButton"
              disabled={_.isEmpty(this.selectedQueryName()) || _.isEmpty(this.selectedProcessId())}
              title={queryButtonTooltip}
              onClick={this.queryState.bind(this, this.selectedProcessId(), this.selectedQueryName(), this.state.key)}
            >{t("queriedState.query.button", "Query")}</ButtonWithFocus>
          </div>
        </div>
      )
    }
    const tableRender = () => {
      return (
        <Table className="esp-table" data={this.genericPrettyPrintedState(this.state.fetchedState)} sortable={true}/>
      )
    }
    const toRender = () => (
      <div>
        {queryFormRender()}
        {_.isEmpty(this.state) ? null : tableRender()}
      </div>
    )
    return toRender()
  }

  selectedProcessId = () => {
    return this.state.processId || this.props.initialState.processId
  }

  selectedQueryName = () => {
    return this.state.queryName || this.props.initialState.queryName
  }

  processesForQueryName = (queryName) => {
    return _.isEmpty(queryName) ? [] : this.props.availableQueryableStates[queryName]
  }

  queryState = (processId, queryName, key) => {
    this.setState({fetchedState: []})
    return HttpService.queryState(processId, queryName, key).then(response => {
      this.setState({fetchedState: response.data})
    })
  }

  //this is kind of hack, but better here than in engine
  genericPrettyPrintedState = (fetchedState) => {
    if (_.isArray(fetchedState)) {
      return fetchedState.map((entry, idx) => {
        return _.mapValues(entry, (value, key) => {
          if (key.toLowerCase().includes("timestamp")) {
            return Moment(value).format(DATE_FORMAT)
          } else if (_.isObject(value)) {
            return this.renderJsonTree(value)
          } else {
            return value
          }
        })
      })
    } else if (_.isObject(fetchedState)){
      return [{value: this.renderJsonTree(fetchedState)}]
    } else {
      return "error during displaying state"
    }
  }

  renderJsonTree = (value) => {
    return (
      <JSONTree
        data={value}
        hideRoot={true}
        theme={{
          label: {
            fontWeight: "normal",
          },
          tree: {
            backgroundColor: "none",
          },
        }}
      />
    )
  }

}

function mapState(state) {
  const availableQueryableStates = state.settings.availableQueryableStates
  const firstQueryName = _.keys(availableQueryableStates)[0]
  const firstProcessId = _.isEmpty(firstQueryName) ? "" : availableQueryableStates[firstQueryName][0]
  return {
    availableQueryableStates: state.settings.availableQueryableStates,
    initialState: {processId: firstProcessId, queryName: firstQueryName},
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(QueriedStateTable)
