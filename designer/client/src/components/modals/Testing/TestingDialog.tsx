import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import { useState } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithEventsData } from "../../../actions/nk/displayTestResults";
import { getTestCapabilities, getTestingEventParameters } from "../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import type { WindowKind } from "../../../windowManager";
import { WindowContent } from "../../../windowManager";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import type { TestingEventParameters } from "./TestingEventsTable";
import { TestingEventsTable } from "./TestingEventsTable";

type DocsLink = {
    url: string;
    label?: string;
};

export type TestingViewParams = {
    Icon?: ElementType;
    docs?: DocsLink;
    // may contain a ::form-fields or ::form-field{name=""} directives
    markdownContent?: string;
};

export interface TestingData {
    viewParams: TestingViewParams;
}

function TestingDialog(props: WindowContentProps<WindowKind, TestingData>): ReactElement {
    const { t } = useTranslation();
    const { data, close } = props;
    const {
        meta: { viewParams },
        kind,
    } = data;
    const dispatch = useAppDispatch();
    const testCapabilities = useAppSelector(getTestCapabilities);
    const defaultParameter = testCapabilities.testWithParameters.sourceParameters[0];
    const defaultEvent = useMemo(
        () => ({
            sourceId: defaultParameter.sourceId,
            timestamp: undefined,
            variables: testCapabilities.testWithParameters.sourceParameters[0].parameters[0].defaultValue.expression,
        }),
        [defaultParameter.sourceId, testCapabilities.testWithParameters.sourceParameters],
    );
    const testingEventsParameters = useAppSelector(getTestingEventParameters);

    const [events, setEvents] = useState<TestingEventParameters[]>(testingEventsParameters || [defaultEvent]);
    const sourceOptions = testCapabilities.testWithParameters.sourceParameters.flatMap((sourceParameter) => sourceParameter.sourceId);

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("testingForm.cancelButton.label", "Cancel"), action: () => close(), classname: LoadingButtonTypes.secondaryButton },
            {
                title: t("testingForm.testButton.label", "Test"),
                action: () => {
                    try {
                        dispatch(testScenarioWithEventsData(events));
                        close();
                    } catch (e) {
                        console.error(e.message);
                    }
                },
            },
        ],
        [close, dispatch, events, t],
    );

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={viewParams.Icon} type={kind} />}
            subheader={<NodeDocs name={viewParams.docs?.label} href={viewParams.docs?.url} />}
            buttons={buttons}
        >
            <ContentSize>
                <TestingEventsTable
                    sourceOptions={sourceOptions}
                    sourceParameters={testCapabilities.testWithParameters.sourceParameters}
                    data={events}
                    onDataChange={(data) => {
                        setEvents(data);
                    }}
                    defaultEvent={defaultEvent}
                />
            </ContentSize>
        </WindowContent>
    );
}
export default TestingDialog;
