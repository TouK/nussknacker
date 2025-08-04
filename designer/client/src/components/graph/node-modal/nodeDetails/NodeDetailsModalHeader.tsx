import { styled } from "@mui/material";
import { isEmpty } from "lodash";
import type { PropsWithChildren, ReactElement } from "react";
import React, { useMemo } from "react";

import nodeAttributes from "../../../../assets/json/nodeAttributes.json";
import ProcessUtils from "../../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../../reducers/selectors/getProcessDefinitionData";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types";
import { ComponentIcon } from "../../../toolbars/creator/ComponentIcon";
import { IconModalTitle } from "./IconModalTitle";
import { ModalHeader, WindowHeaderIconStyled } from "./NodeDetailsStyled";
import { NodeDocs } from "./SubHeader";

const getNodeAttributes = (node: NodeType) => {
    return nodeAttributes[node.type] || node;
};

type IconModalHeaderProps = PropsWithChildren<{
    startIcon?: React.ReactElement;
    endIcon?: React.ReactElement;
    subheader?: React.ReactElement;
    className?: string;
}>;

export function IconModalHeader({ subheader, className, ...props }: IconModalHeaderProps) {
    return (
        <ModalHeader className={className}>
            <IconModalTitle
                sx={{
                    textTransform: "lowercase",
                    span: { px: 1.6 },
                }}
                {...props}
            />
            {subheader}
        </ModalHeader>
    );
}

export const getNodeDetailsModalTitle = (node: NodeType): string => {
    const { name } = getNodeAttributes(node);
    const variableLanguage = node?.value?.language;
    return (isEmpty(variableLanguage) ? "" : `${variableLanguage} `) + name;
};

export const NodeDetailsModalSubheader = ({ node }: { node: NodeType }): ReactElement => {
    const { components = {} } = useAppSelector(getProcessDefinitionData);

    const docsUrl = useMemo(() => {
        return ProcessUtils.extractComponentDefinition(node, components)?.docsUrl;
    }, [components, node]);
    const label = useMemo(() => {
        return ProcessUtils.extractComponentDefinition(node, components)?.label;
    }, [components, node]);

    return <NodeDocs name={label} href={docsUrl} />;
};

export const NodeDetailsModalIcon = styled(WindowHeaderIconStyled.withComponent(ComponentIcon))(({ node, theme }) => ({
    backgroundColor: theme.palette.custom.getNodeStyles(node.type).fill,
}));
