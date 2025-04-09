import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import { useMemo } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import type { WindowKind } from "../../../windowManager";
import { WindowContent } from "../../../windowManager";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import { TestingProvider, useTesting } from "./TestingContext";
import { TestingForm } from "./TestingForm";

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
    const { isValid, action } = useTesting();

    const buttons: WindowButtonProps[] = useMemo(
        () => [
            { title: t("testingForm.cancelButton.label", "Cancel"), action: () => close(), classname: LoadingButtonTypes.secondaryButton },
            { title: t("testingForm.testButton.label", "Test"), action: () => action(), disabled: !isValid },
        ],
        [action, close, isValid, t],
    );

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={viewParams.Icon} type={kind} />}
            subheader={<NodeDocs name={viewParams.docs?.label} href={viewParams.docs?.url} />}
            buttons={buttons}
        >
            <ContentSize>
                <TestingForm testingData={props.data} closeDialog={close} />
            </ContentSize>
        </WindowContent>
    );
}

const TestingDialogWithProvider = (props: WindowContentProps<WindowKind, TestingData>) => (
    <TestingProvider>
        <TestingDialog {...props} />
    </TestingProvider>
);
export default TestingDialogWithProvider;
