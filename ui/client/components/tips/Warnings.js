import InlinedSvgs from "../../assets/icons/InlinedSvgs"
import React from "react"
import HeaderIcon from "./HeaderIcon"
import {v4 as uuid4} from "uuid";
import {Link} from "react-router-dom"
import NodeUtils from "../graph/NodeUtils"
import PropTypes from "prop-types"

export default class Warnings extends React.Component {

  static propTypes = {
    warnings: PropTypes.array.isRequired,
    showDetails: PropTypes.func.isRequired,
    currentProcess: PropTypes.object.isRequired,
  }

  render() {
    const {warnings, showDetails, currentProcess} = this.props
    const groupedByMessage = _.groupBy(warnings, warning => warning.error.message)
    const separator = ", "

    return (
      <div key={uuid4()}>
        {warnings.length > 0 && <HeaderIcon className={"icon"} icon={InlinedSvgs.tipsWarning}/>}
        <div>
          {
            Object.entries(groupedByMessage).map(([message, warnings]) =>
              <div key={uuid4()}
                   className={"warning-tips"}
                   title={warnings.description}>
                <span>{headerMessageByWarningMessage.get(message)}</span>
                <div className={"warning-links"}>
                  {
                    warnings.map((warning, index) =>
                      <Link key={uuid4()}
                            className={"node-warning-link"}
                            to={""}
                            onClick={event => showDetails(event, NodeUtils.getNodeById(warning.key, currentProcess))}>
                        <span>{warning.key}</span>
                        {(index < warnings.length - 1) ? separator : null}
                      </Link>)
                  }
                </div>
              </div>)
          }
        </div>
      </div>
    )
  }
}

const headerMessageByWarningMessage = new Map([["Node is disabled", "Node disabled: "]]);

