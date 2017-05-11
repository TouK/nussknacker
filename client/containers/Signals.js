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
    HttpService.fetchSignals().then(signals => {
      const firstSignal = _.head(signals)
      this.setState(
        {signals: signals, signalType: firstSignal.name, processId : _.head(firstSignal.availableProcesses)}
      )
    })
  }

  initialState(props) {
    return {signalType: '', processId: null, signalParams: {}, signals: {}}
  }

  render() {
    const currentSignal = this.findSignal(this.state.signalType)
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
                <select className="node-input" onChange={(e) => {
                  const nextSignalType = e.target.value
                  this.setState({signalType: nextSignalType, signalParams: {}, processId: this.firstProcessForSignal(nextSignalType)})
                }}>
                  {_.map(this.state.signals, (sig, index) => (<option key={index} value={sig.name}>{sig.name}</option>))}
                </select>
              </div>
            </div>
            <div className="node-row">
              <div className="node-label">Process id</div>
              <div className="node-value">
                <select className="node-input" onChange={(e) => this.setState({processId: e.target.value})}>
                  {(currentSignal.availableProcesses || [])
                    .map((process, index) => (<option key={index} value={process}>{process}</option>))}
                </select>
              </div>
            </div>
            {_.get(currentSignal, 'parameters', []).map((param, idx) => {
              return (
                <div className="node-row" key={idx}>
                  <div className="node-label">{param}</div>
                  <div className="node-value">
                    <input className="node-input" type="text" value={this.state.signalParams[param] || ""} onChange={(e) => this.changeParamValue(param, e.target.value)}/>
                  </div>
                </div>
              )
            }) }
          </div>
          {!_.isEmpty(this.state.signalType) ?
            <button type="button" className="modalButton"
                    onClick={this.sendSignal.bind(this, this.state.signalType, this.state.processId, this.state.signalParams)}>Send signal</button>
            : null}
        </div>
    </div>
    </div>
    )
  }

  firstProcessForSignal = (signalType) => {
    const signalForType = this.findSignal(signalType)
    return _.head(signalForType.availableProcesses)
  }

  findSignal = (signalType) => {
    return _.find(this.state.signals, sig => sig.name == signalType) || {}
  }

  sendSignal = (signalType, processId, signalParams) => {
    return HttpService.sendSignal(signalType, processId, signalParams)
  }

  changeParamValue = (paramName, newValue) => {
    const newSignalParams = _.cloneDeep(this.state.signalParams)
    _.set(newSignalParams, paramName, newValue)
    this.setState({signalParams: newSignalParams})
  }


}

Signals.path = "/signals"
Signals.header = "Signals"

function mapState(state) {
  return {
    processingType: 'streaming',
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Signals);