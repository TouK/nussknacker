import React from "react"
import {connect} from "react-redux"
import {UnknownRecord} from "../types/common"
import NotFound from "./errors/NotFound"
import * as queryString from "query-string"

type Props = {
    settings: $TodoType[],
    match: $TodoType,
  }

export class CustomTabs extends React.Component<Props> {

  static path: string
  static header: string

  constructor(props) {
    super(props)
  }

  render() {
    const id = this.props.match.params.id
    const ref = "customTabsFrame"
    const tab = this.props.settings
      .find(o => o.id == id)

    const tabUrl = queryString.stringifyUrl({
      url: tab.url,
      query: {
        iframe: "true",
      },
    })

    if (tab) {
      return (
        <div className="Page">
          <iframe
            ref={ref}
            src={tabUrl}
            width="100%"
            height={window.innerHeight}
            frameBorder="0"
          />
        </div>
      )
    } else {
      return (<NotFound/>)
    }
  }
}

CustomTabs.path = "/customtabs"
CustomTabs.header = "customtabs"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.customTabs || [],
  }
}

export default connect(mapState)(CustomTabs)
