import PropTypes from "prop-types"
import React from "react"
import {connect} from "react-redux"

class Search extends React.Component {
  render() {
    if (this.props.settings.url) {
      return (
        <div className="Page">
          <iframe
            ref="metricsFrame"
            src={this.props.settings.url}
            width="100%"
            height={window.innerHeight}
            frameBorder="0"
          />
        </div>
      )
    }

    return (<div/>)
  }
}

Search.propTypes = {
  settings: PropTypes.object.isRequired,
}

Search.path = "/search"
Search.header = "Search"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.search || {},
  }
}

export default connect(mapState)(Search)
