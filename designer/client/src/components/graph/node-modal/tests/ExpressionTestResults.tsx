import { Visibility, VisibilityOff } from "@mui/icons-material";
import InfoIcon from "@mui/icons-material/Info";
import { FormControl, FormLabel } from "@mui/material";
import React, { useRef, useState } from "react";
import type { PropsWithChildren } from "react";

import type { NodeResultsForContext } from "../../../../common/TestResultUtils";
import { InfoTooltip } from "../editors/InfoTooltip";
import { HIDDEN_TEXTAREA_PIXEL_HEIGHT } from "../NodeDetailsContent/NodeTableStyled";
import TestValue from "./TestValue";

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
                    <InfoTooltip text={"Value evaluated in test case"} variant={"hover"}>
                        <InfoIcon sx={() => ({ alignSelf: "center" })} />
                    </InfoTooltip>
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
