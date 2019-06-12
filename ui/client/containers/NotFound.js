import React from 'react'
import {NavLink, withRouter} from 'react-router-dom'
import EspApp from "./EspApp";


class NotFound extends React.Component {

  constructor(props) {
    super(props);
  }

  render() {
    return (
      <div className="error-template center-block">
        <h1>Oops!</h1>
        <h2>404 Not Found</h2>
        <div className="error-details">
          Requested page: <strong>{this.props.history.location.pathname}</strong> not found!
        </div>

        <div className="error-actions">
          <NavLink to={EspApp.path} className="btn btn-primary btn-lg">
            <span className="glyphicon glyphicon-home"></span> Take Me Home
          </NavLink>
        </div>
      </div>
    )
  }
}

NotFound.title = 'Page Not Found'
NotFound.header = 'Page Not Found'

export default withRouter(NotFound);