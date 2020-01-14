import React from "react"
import {withRouter} from "react-router-dom"

class InitializeError extends React.Component {
  defaultOnButtonClick = () => {
    window.location.reload()
  }

  render() {
    return (
      <div className="error-template center-block">
        <h1>Oops!</h1>
        <h2>{this.props.error.message || "Application Unexpected Error"}</h2>
        <div className="error-details">
          <br/>
          <p>
            {
              this.props.error.description || "An unexpected error seems to have occurred. Please contact with system administrators."
            }
          </p>
          {
            this.props.error.showButton === false ? null : (
              <div
                className="big-blue-button"
                role="button"
                style={{float: "0 auto"}}
                onClick={this.props.error.buttonOnClick || this.defaultOnButtonClick}
              >
                {
                  this.props.error.buttonLabel || "Try to refresh page"
                }
              </div>
            )
          }
        </div>
      </div>
    )
  }
}

InitializeError.defaultProps = {
  error: {
    buttonOnClick: null,
    buttonLabel: null,
    message: null,
    description: null,
    showButton: null,
  },
}

export default withRouter(InitializeError)
