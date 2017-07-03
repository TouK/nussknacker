import React from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import HttpService from "../http/HttpService";
import InlinedSvgs from '../assets/icons/InlinedSvgs'

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
        <div className="icon" title="Warning" dangerouslySetInnerHTML={{__html: InlinedSvgs.tipsWarning}} />
        <span className="errorText">{this.state.healthCheck.error}</span>
      </div>) : null

  }
}

export default HealthCheck;
