import { get, has, isEmpty } from "lodash";
import React, { PropsWithChildren, ReactElement, useMemo } from "react";
import { useSelector } from "react-redux";
import nodeAttributes from "../../../../assets/json/nodeAttributes.json";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { NodeType } from "../../../../types";
import NodeUtils from "../../NodeUtils";
import ProcessUtils from "../../../../common/ProcessUtils";
import { IconStyled, ModalHeader } from "./NodeDetailsStyled";
import { NodeDocs } from "./SubHeader";
import { IconModalTitle } from "./IconModalTitle";
import { useTheme } from "@mui/material";
import { useComponentIcon } from "../../../toolbars/creator/ComponentIcon";

const nodeClassProperties = [`service.id`, `ref.typ`, `nodeType`, `ref.id`];

const findNodeClass = (node: NodeType) =>
    get(
        node,
        nodeClassProperties.find((property) => has(node, property)),
    );

const getNodeAttributes = (node: NodeType) => nodeAttributes[NodeUtils.nodeType(node)];

type IconModalHeaderProps = PropsWithChildren<{
    startIcon?: React.ReactElement;
    endIcon?: React.ReactElement;
    subheader?: React.ReactElement;
    className?: string;
}>;

function IconModalHeader({ subheader, className, ...props }: IconModalHeaderProps) {
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

export const NodeDetailsModalHeader = ({ node, ...props }: { node: NodeType; className?: string }): ReactElement => {
    const { components = {} } = useSelector(getProcessDefinitionData);

    const docsUrl = useMemo(() => ProcessUtils.extractComponentDefinition(node, components)?.docsUrl, [components, node]);
    const { name } = getNodeAttributes(node);
    const variableLanguage = node?.value?.language;
    const nodeClass = findNodeClass(node);

    const header = (isEmpty(variableLanguage) ? "" : `${variableLanguage} `) + name;
    const src = useComponentIcon(node);
    const theme = useTheme();
    const backgroundColor = theme.palette.custom.nodes[NodeUtils.nodeType(node)].fill;

    return (
        <IconModalHeader
            startIcon={<IconStyled src={src} sx={{ backgroundColor }} />}
            subheader={<NodeDocs name={nodeClass} href={docsUrl} />}
            {...props}
        >
            {header}
        </IconModalHeader>
    );
};
