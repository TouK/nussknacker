import i18next from "i18next";
import { isEmpty } from "lodash";
import React from "react";

import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import { StyledTipsContentWrapper } from "./styled";
import type { Props } from "./TestCaseErrors";

export const TestCaseErrorTips = ({ testCasesValidationErrors, showDetails, scenario }: Props) => {
    const invalidNodeIds = Object.keys(testCasesValidationErrors);

    return isEmpty(testCasesValidationErrors) ? null : (
        <StyledTipsContentWrapper>
            <NodeErrorsLinkSection
                nodeIds={invalidNodeIds}
                message={i18next.t("errors.testCaseErrorsIn", "Test case errors in: ")}
                showDetails={showDetails}
                scenario={scenario}
            />
        </StyledTipsContentWrapper>
    );
};
