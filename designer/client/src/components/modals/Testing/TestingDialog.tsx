import { Box } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import React, { useState } from "react";

import type { ScenarioGraph, UIParameter, VariableTypes } from "../../../types";
import type { WindowKind } from "../../../windowManager";
import { WindowContent } from "../../../windowManager";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import type { ActionValues } from "../AdhocTesting/AdhocTestingFormContext";
import type { FormValue, TouchedValue } from "./TestingForm";
import { TestingForm, TestType } from "./TestingForm";

type DocsLink = {
    url: string;
    label?: string;
};

export type TestingViewParams = {
    confirmText?: string;
    cancelText?: string;
    Icon?: ElementType;
    docs?: DocsLink;
    // may contain a ::form-fields or ::form-field{name=""} directives
    markdownContent?: string;
};

export interface TestingParameters {
    parameters: UIParameter[];
    variableTypes: VariableTypes;
    processingType: string;
    scenarioName: string;
    initialValues: ActionValues;
    onConfirmAction: (values: ActionValues) => void;
    sourceId: string;
    scenarioGraph: ScenarioGraph;
}

export interface TestingData {
    view: TestingViewParams;
}

function TestingDialog(props: WindowContentProps<WindowKind, TestingData>): ReactElement {
    const { data, close } = props;
    const {
        meta: { view },
        kind,
    } = data;

    const [testType, setState] = useState<FormValue>({
        testType: TestType.withParameters,
    });
    const [touched, setTouched] = useState<TouchedValue>({
        testType: false,
    });
    const onChange = (value: FormValue) => {
        setState(value);
    };
    const handleSetTouched = (touched: TouchedValue) => {
        setTouched(touched);
    };

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={view.Icon} type={kind} />}
            subheader={<NodeDocs name={view.docs?.label} href={view.docs?.url} />}
        >
            <ContentSize>
                <Box mx={3}>
                    <TestingForm
                        value={testType}
                        onChange={onChange}
                        touched={touched}
                        handleSetTouched={handleSetTouched}
                        testingData={props.data}
                        closeDialog={() => close()}
                    />
                </Box>
            </ContentSize>
        </WindowContent>
    );
}

export default TestingDialog;
