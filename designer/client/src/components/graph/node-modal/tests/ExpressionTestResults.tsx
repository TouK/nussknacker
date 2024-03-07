import React, { PropsWithChildren, useEffect, useRef, useState } from "react";
import InfoIcon from "@mui/icons-material/Info";
import NodeTip from "../NodeTip";
import TestValue from "./TestValue";
import { NodeResultsForContext } from "../../../../common/TestResultUtils";
import { Visibility, VisibilityOff } from "@mui/icons-material";
import { FormControl, FormLabel } from "@mui/material";
import { HiddenTextareaPixelHeight } from "../NodeDetailsContent/NodeTableStyled";

interface ExpressionTestResultsProps {
    fieldName: string;
    resultsToShow: NodeResultsForContext;
}

export default function ExpressionTestResults(props: PropsWithChildren<ExpressionTestResultsProps>): JSX.Element {
    const { fieldName, resultsToShow } = props;
    const [hideTestResults, toggleTestResults] = useState(false);
    const [fitsMaxHeight, setFitsMaxHeight] = useState(true);
    const testValueRef: React.Ref<HTMLTextAreaElement> = useRef(null);
    useEffect(() => {
        if (testValueRef.current && testValueRef.current.scrollHeight > HiddenTextareaPixelHeight) {
            toggleTestResults((s) => !s);
            setFitsMaxHeight(false);
        }
    }, []);

    const testValue = fieldName ? resultsToShow && resultsToShow.expressionResults[fieldName] : null;
    const PrettyIconComponent = hideTestResults ? VisibilityOff : Visibility;

    return testValue ? (
        <div>
            {props.children}
            <FormControl>
                <FormLabel>
                    <NodeTip
                        title={"Value evaluated in test case"}
                        icon={<InfoIcon sx={(theme) => ({ color: theme.custom.colors.info, alignSelf: "center" })} />}
                    />
                    {testValue.pretty && !fitsMaxHeight ? (
                        <PrettyIconComponent sx={{ cursor: "pointer" }} onClick={() => toggleTestResults((s) => !s)} />
                    ) : null}
                </FormLabel>
                <TestValue ref={testValueRef} value={testValue} shouldHideTestResults={hideTestResults} />
            </FormControl>
        </div>
    ) : (
        <>{props.children}</>
    );
}
