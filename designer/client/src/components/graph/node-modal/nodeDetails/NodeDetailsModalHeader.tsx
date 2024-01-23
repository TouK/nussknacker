import { get, has, isEmpty } from "lodash";
import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import nodeAttributes from "../../../../assets/json/nodeAttributes.json";
import NkModalStyles from "../../../../common/NkModalStyles";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { NodeType } from "../../../../types";
import NodeUtils from "../../NodeUtils";
import ProcessUtils from "../../../../common/ProcessUtils";
import { ComponentIconStyled, ModalHeader, ModalTitleContainer, NodeDetailsModalTitle } from "./NodeDetailsStyled";
import { NodeClassDocs } from "./SubHeader";

const nodeClassProperties = [`service.id`, `ref.typ`, `nodeType`, `ref.id`];

const findNodeClass = (node: NodeType) =>
    get(
        node,
        nodeClassProperties.find((property) => has(node, property)),
    );

const getNodeAttributes = (node: NodeType) => nodeAttributes[NodeUtils.nodeType(node)];

const NodeDetailsModalHeader = ({ node }: { node: NodeType }): JSX.Element => {
    const { components = {} } = useSelector(getProcessDefinitionData);
    const docsUrl = useMemo(() => ProcessUtils.extractComponentDefinition(node, components)?.docsUrl, [components, node]);

    const attributes = getNodeAttributes(node);
    const titleStyles = NkModalStyles.headerStyles(attributes.styles.fill, attributes.styles.color);
    const variableLanguage = node?.value?.language;
    const header = (isEmpty(variableLanguage) ? "" : `${variableLanguage} `) + attributes.name;

    const nodeClass = findNodeClass(node);

    return (
        <ModalHeader onDragStart={(e) => e.preventDefault()}>
            <ModalTitleContainer>
                <NodeDetailsModalTitle style={titleStyles}>
                    <ComponentIconStyled node={node} />
                    <span>{header}</span>
                </NodeDetailsModalTitle>
            </ModalTitleContainer>
            <NodeClassDocs nodeClass={nodeClass} docsUrl={docsUrl} />
        </ModalHeader>
    );
};

export default NodeDetailsModalHeader;
