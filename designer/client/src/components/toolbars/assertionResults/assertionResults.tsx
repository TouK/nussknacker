import React from "react";

import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";

const AssertionResults = () => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    return (
        <ToolbarWrapper id={"assertion-results-panel"} title={"Assertions result"}>
            {Object.keys(testAssertionResults).map((testAssertion) => {
                return testAssertion;
            })}
        </ToolbarWrapper>
    );
};

export default AssertionResults;
