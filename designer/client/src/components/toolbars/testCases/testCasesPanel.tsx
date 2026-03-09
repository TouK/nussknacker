import React from "react";

import { getTestCase } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { TestCaseExpandable } from "./testCase";

const TestCasesPanel = () => {
    const testCase = useAppSelector(getTestCase);

    return (
        <ToolbarWrapper id={"test-cases-panel"} title={"Test cases"}>
            <TestCaseExpandable testCase={testCase} />
        </ToolbarWrapper>
    );
};

export default TestCasesPanel;
