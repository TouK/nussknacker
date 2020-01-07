import React from "react"
import {withRouter} from "react-router-dom"


class NotFound extends React.Component {
  render() {
    return (
      <div className="error-template center-block">
        <h1>Oops!</h1>
        <h2>{this.props.message || "That page can’t be found..."}</h2>
        <div className="error-details">
          <br/>
          <p>
            It looks like nothing was found at this location.
            Maybe try one of the links in the menu or press back to go to the previous page.
          </p>
        </div>
      </div>
    )
  }
}

export default withRouter(NotFound)