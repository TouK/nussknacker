import PropTypes from "prop-types";
import React from "react";
import { Link } from "react-router-dom";
import { v4 as uuid4 } from "uuid";
import TipsWarning from "../../assets/img/icons/tipsWarning.svg";
import NodeUtils from "../graph/NodeUtils";
import { groupBy } from "lodash";

export default class Warnings extends React.Component {
    static propTypes = {
        warnings: PropTypes.array.isRequired,
        showDetails: PropTypes.func.isRequired,
        currentProcess: PropTypes.object.isRequired,
    };

    render() {
        const { warnings, showDetails, currentProcess } = this.props;
        const groupedByType = groupBy(warnings, (warning) => warning.error.typ);
        const separator = ", ";

        return (
            <div key={uuid4()}>
                {warnings.length > 0 && <TipsWarning className={"icon"} />}
                <div>
                    {Object.entries(groupedByType).map(([warningType, warnings]) => (
                        <div key={uuid4()} className={"warning-tips"} title={warnings.description}>
                            <span>{headerMessageByWarningType.get(warningType)}</span>
                            <div className={"warning-links"}>
                                {warnings.map((warning, index) => (
                                    <Link
                                        key={uuid4()}
                                        className={"node-warning-link"}
                                        to={""}
                                        onClick={(event) => showDetails(event, NodeUtils.getNodeById(warning.key, currentProcess))}
                                    >
                                        <span>{warning.key}</span>
                                        {index < warnings.length - 1 ? separator : null}
                                    </Link>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }
}

const headerMessageByWarningType = new Map([["DisabledNode", "Nodes disabled: "]]);
