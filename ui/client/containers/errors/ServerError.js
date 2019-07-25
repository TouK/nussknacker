import React from 'react'
import {withRouter} from 'react-router-dom'


class ServerError extends React.Component {
  render() {
    return (
      <div className="error-template center-block">
        <h1>Oops!</h1>
        <h2>{this.props.message || "Internal Server Error"}</h2>
        <div className="error-details">
          <br />
          <p>
          {
            this.props.description || "An unexpected error seems to have occurred. Why not try refreshing your page? Or you can contact with administrators."
          }
          </p>
        </div>
      </div>
    )
  }
}

export default withRouter(ServerError)