import PropTypes from "prop-types"
import React from "react"
import {connect} from "react-redux"
import {Page} from "./Page"
import {getFeatureSettings} from "../reducers/selectors/settings"

export class Search extends React.Component {
  render() {
    if (this.props.settings.url) {
      return (
        <Page>
          <iframe
            ref="metricsFrame"
            src={this.props.settings.url}
            width="100%"
            height={window.innerHeight}
            frameBorder="0"
          />
        </Page>
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
    settings: getFeatureSettings(state).search || {},
  }
}

export default connect(mapState)(Search)
