import React from "react";
import { v4 as uuid4 } from "uuid";
import TipsWarning from "../../assets/img/icons/tipsWarning.svg";
import NodeUtils from "../graph/NodeUtils";
import { groupBy } from "lodash";
import { LinkStyled } from "./Styled";
import { Process } from "../../types";

interface Warning {
    error: {
        typ: string;
        description: string;
    };
    key: string;
}

interface WarningsProps {
    warnings: Warning[];
    showDetails: (event: React.MouseEvent, node) => void; // You can specify the actual type for "node" here
    currentProcess: Process;
}

const headerMessageByWarningType = new Map([["DisabledNode", "Nodes disabled: "]]);

const Warnings = ({ warnings, showDetails, currentProcess }: WarningsProps) => {
    const groupedByType = groupBy(warnings, (warning) => warning.error.typ);
    const separator = ", ";

    return (
        <div key={uuid4()} style={{ display: "flex" }}>
            {warnings.length > 0 && <TipsWarning className={"icon"} style={{ width: 16, marginRight: 5 }} />}
            <div>
                {Object.entries(groupedByType).map(([warningType, warnings]) => (
                    <div key={uuid4()} className={"warning-tips"} title={warnings[0]?.error.description}>
                        <span>{headerMessageByWarningType.get(warningType)}</span>
                        <div style={{ display: "inline" }}>
                            {warnings.map((warning, index) => (
                                <LinkStyled
                                    key={uuid4()}
                                    to={""}
                                    onClick={(event) => showDetails(event, NodeUtils.getNodeById(warning.key, currentProcess))}
                                >
                                    <span>{warning.key}</span>
                                    {index < warnings.length - 1 ? separator : null}
                                </LinkStyled>
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default Warnings;
