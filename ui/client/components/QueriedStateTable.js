import React from "react"
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import _ from "lodash";
import Moment from "moment"
import {Table} from "reactable";
import JSONTree from "react-json-tree"
import {dateFormat} from "../config";

class QueriedStateTable extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      initialState: {},
      key: ""
    }
  }

  componentDidMount() {
    this.props.actions.fetchAvailableQueryStates()
  }

  render() {
    const queryFormRender = () => {
      let queryButtonTooltip
      if (_.isEmpty(this.selectedQueryName())) {
        queryButtonTooltip = "Query name is not selected"
      } else if (_.isEmpty(this.selectedProcessId())) {
        queryButtonTooltip = "Process id is not selected"
      }
      return (
        <div>
          <div className="esp-form-row">
            <p>Query name</p>
            <select value={this.selectedQueryName()} onChange={(e) =>
              this.setState({queryName: e.target.value, processId: this.processesForQueryName(e.target.value)[0]})}>
              {_.keys(this.props.availableQueryableStates).map((queryName, index) => (<option key={index} value={queryName}>{queryName}</option>))}
            </select>
          </div>
          <div className="esp-form-row">
            <p>Process id</p>
            <select value={this.selectedProcessId()} onChange={(e) => this.setState({processId: e.target.value})}>
              {this.processesForQueryName(this.selectedQueryName()).map((processId, index) => (<option key={index} value={processId}>{processId}</option>))}
            </select>
          </div>
          <div className="esp-form-row">
            <p>Key (optional)</p>
            <input value={this.state.key} onChange={(e) => this.setState({key: e.target.value})}/>
            <button type="button" className="modalButton" disabled={_.isEmpty(this.selectedQueryName()) || _.isEmpty(this.selectedProcessId())} title={queryButtonTooltip}
                    onClick={this.queryState.bind(this, this.selectedProcessId(), this.selectedQueryName(), this.state.key)}>Query</button>
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
            return Moment(value).format(dateFormat)
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
      <JSONTree data={value} hideRoot={true} theme={{
        label: {
          fontWeight: "normal",
        },
        tree: {
          backgroundColor: "none"
        }
      }}/>
    )
  }

}

function mapState(state) {
  const availableQueryableStates = state.settings.availableQueryableStates
  const firstQueryName = _.keys(availableQueryableStates)[0]
  const firstProcessId = _.isEmpty(firstQueryName) ? "" : availableQueryableStates[firstQueryName][0]
  return {
    availableQueryableStates: state.settings.availableQueryableStates,
    initialState: {processId: firstProcessId, queryName: firstQueryName}
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(QueriedStateTable);