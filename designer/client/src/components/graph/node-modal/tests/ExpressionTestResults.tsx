import React, { PropsWithChildren, useRef, useState } from "react";
import InfoIcon from "@mui/icons-material/Info";
import NodeTip from "../NodeTip";
import TestValue from "./TestValue";
import { NodeResultsForContext } from "../../../../common/TestResultUtils";
import { Visibility, VisibilityOff } from "@mui/icons-material";
import { FormControl, FormLabel } from "@mui/material";
import { HIDDEN_TEXTAREA_PIXEL_HEIGHT } from "../NodeDetailsContent/NodeTableStyled";

interface ExpressionTestResultsProps {
    fieldName: string;
    resultsToShow: NodeResultsForContext;
}

export default function ExpressionTestResults(props: PropsWithChildren<ExpressionTestResultsProps>): JSX.Element {
    const { fieldName, resultsToShow } = props;
    const testValueRef: React.Ref<HTMLTextAreaElement> = useRef(null);
    const fitsMaxHeight = testValueRef?.current ? testValueRef.current.scrollHeight <= HIDDEN_TEXTAREA_PIXEL_HEIGHT : true;
    const [collapsedTestResults, setCollapsedTestResults] = useState(true);
    const testValue = fieldName ? resultsToShow && resultsToShow.expressionResults[fieldName] : null;
    const PrettyIconComponent = collapsedTestResults ? VisibilityOff : Visibility;

    return testValue ? (
        <div>
            {props.children}
            <FormControl>
                <FormLabel>
                    <NodeTip title={"Value evaluated in test case"} icon={<InfoIcon sx={(theme) => ({ alignSelf: "center" })} />} />
                    {testValue.pretty && !fitsMaxHeight ? (
                        <PrettyIconComponent sx={{ cursor: "pointer" }} onClick={() => setCollapsedTestResults((s) => !s)} />
                    ) : null}
                </FormLabel>
                <TestValue ref={testValueRef} value={testValue} shouldHideTestResults={collapsedTestResults && !fitsMaxHeight} />
            </FormControl>
        </div>
    ) : (
        <>{props.children}</>
    );
}
