import { Visibility, VisibilityOff } from "@mui/icons-material";
import InfoIcon from "@mui/icons-material/Info";
import { Box } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { useRef, useState } from "react";

import type { NodeResultsForContext } from "../../../../common/TestResultUtils";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../store/storeHelpers";
import { FormControl, FormLabel } from "../editors/FormControl";
import { InfoTooltip } from "../editors/InfoTooltip/InfoTooltip";
import { HIDDEN_TEXTAREA_PIXEL_HEIGHT, nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { ExpressionEvaluationResult } from "./ExpressionEvaluationResult";
import TestValue from "./TestValue";

interface ExpressionTestResultsProps {
    fieldName: string;
    resultsToShow: NodeResultsForContext;
}

export default function ExpressionTestResults(props: PropsWithChildren<ExpressionTestResultsProps>): React.JSX.Element {
    const { fieldName, resultsToShow } = props;
    const testValueRef: React.Ref<HTMLTextAreaElement> = useRef(null);
    const fitsMaxHeight = testValueRef?.current ? testValueRef.current.scrollHeight <= HIDDEN_TEXTAREA_PIXEL_HEIGHT : true;
    const [collapsedTestResults, setCollapsedTestResults] = useState(true);
    const testValue = fieldName ? resultsToShow && resultsToShow.expressionResults[fieldName] : null;
    const PrettyIconComponent = collapsedTestResults ? VisibilityOff : Visibility;
    const userSettings = useAppSelector(getUserSettings);
    const showInputsAndOutputs = userSettings["node.showInputsAndOutputs"];

    return testValue ? (
        <div>
            {props.children}
            {showInputsAndOutputs ? (
                <FormControl>
                    <FormLabel>
                        <Box />
                    </FormLabel>
                    <Box className={nodeValue} maxWidth={"80%"}>
                        <ExpressionEvaluationResult value={testValue} />
                    </Box>
                </FormControl>
            ) : (
                <FormControl>
                    <FormLabel>
                        <InfoTooltip title={"Expression evaluation result"} variant={"hover"}>
                            <InfoIcon sx={() => ({ alignSelf: "center" })} />
                        </InfoTooltip>
                        {testValue.pretty && !fitsMaxHeight ? (
                            <PrettyIconComponent sx={{ cursor: "pointer" }} onClick={() => setCollapsedTestResults((s) => !s)} />
                        ) : null}
                    </FormLabel>
                    <TestValue ref={testValueRef} value={testValue} shouldHideTestResults={collapsedTestResults && !fitsMaxHeight} />
                </FormControl>
            )}
        </div>
    ) : (
        <>{props.children}</>
    );
}
