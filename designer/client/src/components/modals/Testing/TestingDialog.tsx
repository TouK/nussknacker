import { Box } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import React, { useState } from "react";
import { useSelector } from "react-redux";

import { getTestCapabilities } from "../../../reducers/selectors/graph";
import type { WindowKind } from "../../../windowManager";
import { WindowContent } from "../../../windowManager";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
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

export interface TestingData {
    view: TestingViewParams;
}

function TestingDialog(props: WindowContentProps<WindowKind, TestingData>): ReactElement {
    const { data, close } = props;
    const {
        meta: { view },
        kind,
    } = data;

    const testCapabilities = useSelector(getTestCapabilities);

    const availabilityMap: Record<TestType, boolean> = {
        [TestType.withParameters]: testCapabilities.canTestWithForm,
        [TestType.withGeneratedData]: testCapabilities.canGenerateTestData && testCapabilities.canBeTested,
    };
    const availableTestTypes = Object.entries(availabilityMap)
        .filter(([_, isAvailable]) => isAvailable)
        .map(([key]) => key as TestType);

    const [testType, setState] = useState<FormValue>({
        testType: availableTestTypes[0] ?? "",
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
                        closeDialog={close}
                    />
                </Box>
            </ContentSize>
        </WindowContent>
    );
}

export default TestingDialog;
