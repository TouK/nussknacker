import React from "react";

import { getTestAssertionResults } from "../../../reducers/selectors/testing";
import { useAppSelector } from "../../../store/storeHelpers";
import { Expandable } from "../../common/Expandable";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { AssertionResultForNode } from "./assertionResultForNode";

const AssertionResults = () => {
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    return (
        <ToolbarWrapper id={"assertion-results-panel"} title={"Assertions result"}>
            {Object.keys(testAssertionResults).map((nodeId) => (
                <Expandable key={nodeId} expandableTitle={nodeId} componentId={"node-name"}>
                    <AssertionResultForNode nodeId={nodeId} />
                </Expandable>
            ))}
        </ToolbarWrapper>
    );
};

export default AssertionResults;
