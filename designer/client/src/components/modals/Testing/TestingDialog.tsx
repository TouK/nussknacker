import { Box, styled } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import type { ElementType, ReactElement } from "react";
import React from "react";

import type { WindowKind } from "../../../windowManager";
import { WindowContent } from "../../../windowManager";
import { ContentSize } from "../../graph/node-modal/node/ContentSize";
import { WindowHeaderIconStyled } from "../../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { NodeDocs } from "../../graph/node-modal/nodeDetails/SubHeader";
import { TestingForm } from "./TestingForm";

const StyledContentSize = styled(ContentSize)(({ theme }) => ({
    padding: theme.spacing(0, 1.5, 1.5, 1.5),
}));

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
    const { data, close } = props;
    const {
        meta: { viewParams },
        kind,
    } = data;

    return (
        <WindowContent
            {...props}
            icon={<WindowHeaderIconStyled as={viewParams.Icon} type={kind} />}
            subheader={<NodeDocs name={viewParams.docs?.label} href={viewParams.docs?.url} />}
        >
            <StyledContentSize>
                <TestingForm testingData={props.data} closeDialog={close} />
            </StyledContentSize>
        </WindowContent>
    );
}

export default TestingDialog;
