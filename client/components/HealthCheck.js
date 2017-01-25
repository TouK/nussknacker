import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import HttpService from "../http/HttpService";

class HealthCheck extends React.Component {

  constructor(props) {
    super(props);
    this.state = {healthCheck: {state: 'error', error: 'State unknown'}};
  }

  componentWillMount() {
    HttpService.fetchHealthCheck().then((check) => this.setState({healthCheck: check}))
  }

  render() {
    return !this.state.healthCheck || this.state.healthCheck.error ?
      (<div className="healthCheck">
        <div className={"appState " + this.state.healthCheck.state}/>
        {this.state.healthCheck.error ? (<p className="errorText">{this.state.healthCheck.error}</p>) : null}
      </div>) : null

  }
}

export default HealthCheck;
