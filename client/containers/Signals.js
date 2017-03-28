import React from 'react'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import ActionsUtils from "../actions/ActionsUtils";
import HttpService from "../http/HttpService";
import _ from "lodash";

//to jest na razie troche na pale
class Signals extends React.Component {

  constructor(props) {
    super(props);
    this.state = this.initialState(props)
  }

  componentDidMount() {
    this.props.actions.fetchProcessDefinition(this.props.processingType)
  }

  componentWillReceiveProps(props) {
    this.setState(this.initialState(props))
  }

  initialState(props) {
    return {signalType: _.keys(props.signals)[0] || '', signalParams: {}}
  }

  render() {
    //fixme usunac odniesienia do modalowych klas
    //fixme da sie to bez tylu zagniezdzen?
    return (
      <div className="full-dark">
      <div className="modalContent">
        <div className="node-table">
          <div className="node-table-body">
            <div className="node-row">
              <div className="node-label">Signal type</div>
              <div className="node-value">
                <select className="node-input" onChange={(e) => this.setState({signalType: e.target.value, signalParams: {}})}>
                  {_.keys(this.props.signals).map((sig, index) => (<option key={index} value={sig}>{sig}</option>))}
                </select>
              </div>
            </div>
            {this.readSignalParams().map((param, idx) => {
              return (
                <div className="node-row" key={idx}>
                  <div className="node-label">{param.name}</div>
                  <div className="node-value">
                    <input className="node-input" type="text" value={this.state.signalParams[param.name] || ""} onChange={(e) => this.changeParamValue(param.name, e.target.value)}/>
                  </div>
                </div>
              )
            }) }
          </div>
          {!_.isEmpty(this.state.signalType) ?
            <button type="button" className="modalButton"
                    onClick={this.sendSignal.bind(this, this.state.signalType, this.state.signalParams)}>Send signal</button>
            : null}
        </div>
    </div>
    </div>
    )
  }

  sendSignal = (signalType, signalParams) => {
    return HttpService.sendSignal(signalType, signalParams)
  }

  changeParamValue = (paramName, newValue) => {
    const newSignalParams = _.cloneDeep(this.state.signalParams)
    _.set(newSignalParams, paramName, newValue)
    this.setState({signalParams: newSignalParams})
  }

  readSignalParams = () => {
    return (this.props.signals[this.state.signalType] || {}).parameters || []
  }

}

Signals.path = "/signals"
Signals.header = "Signals"

function mapState(state) {
  return {
    processingType: 'streaming',
    signals: _.get(state.settings.processDefinitionData, 'processDefinition.signals') || {}
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Signals);