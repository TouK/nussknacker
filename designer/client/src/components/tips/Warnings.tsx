import WarningIcon from "@mui/icons-material/Warning";
import { styled, Typography } from "@mui/material";
import { groupBy } from "lodash";
import React from "react";
import { v4 as uuid4 } from "uuid";

import type { NodeType } from "../../types/node";
import type { ScenarioGraph } from "../../types/scenarioGraph";
import NodeUtils from "../graph/NodeUtils";
import { explainValidationProblemPrompt } from "./constants";
import { StyledAskAssistantButton, StyledItemWrapper } from "./error/styled";
import { LinkStyled } from "./Styled";

interface Warning {
    error: {
        typ: string;
        description: string;
    };
    key: string;
}

const StyledWarningIcon = styled(WarningIcon)(({ theme }) => ({
    width: "16px",
    height: "16px",
    alignSelf: "flex-start",
    marginRight: "5px",
    color: theme.palette.warning.main,
}));

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
        <StyledItemWrapper key={uuid4()}>
            {warnings.length > 0 && <StyledWarningIcon />}
            <div>
                {Object.entries(groupedByType).map(([warningType, warnings]) => (
                    <div key={uuid4()} title={warnings[0]?.error.description}>
                        <Typography component={"span"} variant={"body2"}>
                            {headerMessageByWarningType.get(warningType)}
                        </Typography>
                        <div style={{ display: "inline" }}>
                            {warnings.map((warning, index) => {
                                const node = NodeUtils.getNodeById(warning.key, scenarioGraph);
                                return (
                                    <Typography
                                        variant={"body2"}
                                        fontWeight={"bold"}
                                        component={LinkStyled}
                                        key={uuid4()}
                                        to={""}
                                        onClick={(event) => showDetails(event, node)}
                                    >
                                        {node?.name ?? warning.key}
                                        {index < warnings.length - 1 ? separator : null}
                                    </Typography>
                                );
                            })}
                        </div>
                    </div>
                ))}
            </div>
            <StyledAskAssistantButton
                question={`Explain scenario nodes warnings.`}
                realPrompt={explainValidationProblemPrompt(JSON.stringify(warnings))}
            />
        </StyledItemWrapper>
    );
};

export default Warnings;
