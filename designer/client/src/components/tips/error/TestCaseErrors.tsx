import type { SyntheticEvent } from "react";
import React from "react";
import { v4 as uuid4 } from "uuid";

import type { NodeOrPropertiesType } from "../../../types/node";
import type { NodeValidationError } from "../../../types/validation";
import type { Scenario } from "../../Process/types";
import { explainValidationProblemPrompt } from "../constants";
import { HeaderIcon } from "./HeaderIcon";
import { StyledAskAssistantButton, StyledItemWrapper } from "./styled";
import { TestCaseErrorTips } from "./TestCaseErrorTips";

export interface Props {
    testCasesValidationErrors: Record<string, NodeValidationError[]>;
    showDetails: (event: SyntheticEvent, details: NodeOrPropertiesType) => void;
    scenario: Scenario;
}

export function TestCaseErrors({ testCasesValidationErrors = {}, showDetails, scenario }: Props) {
    return (
        <StyledItemWrapper key={uuid4()}>
            <HeaderIcon errors={{ testCasesValidationErrors }} />
            <TestCaseErrorTips testCasesValidationErrors={testCasesValidationErrors} showDetails={showDetails} scenario={scenario} />
            <StyledAskAssistantButton
                question={`Explain scenario test case node errors.`}
                realPrompt={explainValidationProblemPrompt(JSON.stringify(testCasesValidationErrors))}
            />
        </StyledItemWrapper>
    );
}
