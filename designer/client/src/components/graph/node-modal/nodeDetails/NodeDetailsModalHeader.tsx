import { get, has, isEmpty } from "lodash";
import React, { PropsWithChildren, ReactElement, useMemo } from "react";
import { useSelector } from "react-redux";
import nodeAttributes from "../../../../assets/json/nodeAttributes.json";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { NodeType } from "../../../../types";
import ProcessUtils from "../../../../common/ProcessUtils";
import { ModalHeader, WindowHeaderIconStyled } from "./NodeDetailsStyled";
import { NodeDocs } from "./SubHeader";
import { IconModalTitle } from "./IconModalTitle";
import { ComponentIcon } from "../../../toolbars/creator/ComponentIcon";
import { styled } from "@mui/material";

const nodeClassProperties = [`service.id`, `ref.typ`, `nodeType`, `ref.id`];

const findNodeClass = (node: NodeType) =>
    get(
        node,
        nodeClassProperties.find((property) => has(node, property)),
    );

const getNodeAttributes = (node: NodeType) => nodeAttributes[node.type];

type IconModalHeaderProps = PropsWithChildren<{
    startIcon?: React.ReactElement;
    endIcon?: React.ReactElement;
    subheader?: React.ReactElement;
    className?: string;
}>;

export function IconModalHeader({ subheader, className, ...props }: IconModalHeaderProps) {
    console.log("From icon modal header");
    console.log(className);
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
    const { components = {} } = useSelector(getProcessDefinitionData);

    const docsUrl = useMemo(() => {
        return ProcessUtils.extractComponentDefinition(node, components)?.docsUrl;
    }, [components, node]);

    const nodeClass = findNodeClass(node);

    return <NodeDocs name={nodeClass} href={docsUrl} />;
};

export const NodeDetailsModalIcon = styled(WindowHeaderIconStyled.withComponent(ComponentIcon))(({ node, theme }) => ({
    backgroundColor: theme.palette.custom.getNodeStyles(node.type).fill,
}));
