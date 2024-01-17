import React from "react";
import { v4 as uuid4 } from "uuid";
import WarningIcon from "@mui/icons-material/Warning";
import NodeUtils from "../graph/NodeUtils";
import { groupBy } from "lodash";
import { LinkStyled } from "./Styled";
import { NodeType, ScenarioGraph } from "../../types";
import { styled } from "@mui/material";

interface Warning {
    error: {
        typ: string;
        description: string;
    };
    key: string;
}

const StyledWarningIcon = styled(WarningIcon)(
    ({ theme }) => `
    width: 16px;
    height: 16px;
    align-self: flex-start;
    margin-right: 5px;
    color: ${theme.custom.colors.warning};
`,
);

interface WarningsProps {
    warnings: Warning[];
    showDetails: (event: React.MouseEvent, node: NodeType) => void;
    scenarioGraph: ScenarioGraph;
}

const headerMessageByWarningType = new Map([["DisabledNode", "Nodes disabled: "]]);

const Warnings = ({ warnings, showDetails, scenarioGraph }: WarningsProps) => {
    const groupedByType = groupBy(warnings, (warning) => warning.error.typ);
    const separator = ", ";

    return (
        <div key={uuid4()} style={{ display: "flex" }}>
            {warnings.length > 0 && <StyledWarningIcon />}
            <div>
                {Object.entries(groupedByType).map(([warningType, warnings]) => (
                    <div key={uuid4()} title={warnings[0]?.error.description}>
                        <span>{headerMessageByWarningType.get(warningType)}</span>
                        <div style={{ display: "inline" }}>
                            {warnings.map((warning, index) => (
                                <LinkStyled
                                    key={uuid4()}
                                    to={""}
                                    onClick={(event) => showDetails(event, NodeUtils.getNodeById(warning.key, scenarioGraph))}
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
