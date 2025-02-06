import { cloneDeep, defaultsDeep, isArray, mergeWith } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { getConfiguredAdditionalComponents } from "../../../reducers/cloudData";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeType } from "../../../types";
import NodeUtils from "../NodeUtils";
import { NodeGroupContentProps } from "./node/NodeGroupContent";

type NodeSwitcherProps = NodeGroupContentProps & {
    componentsNamesToSelect: string[];
};

export function NodeSwitcher({ node, onChange, edges, componentsNamesToSelect = [] }: NodeSwitcherProps) {
    const processDefinitionData = useSelector(getProcessDefinitionData);

    const componentsToSelect = useMemo(() => {
        return processDefinitionData.componentGroups
            .flatMap((g) => g.components)
            .filter((c) => componentsNamesToSelect.includes(c.componentId));
    }, [componentsNamesToSelect, processDefinitionData.componentGroups]);

    const creatorType = useMemo(() => getCreatorType(node), [node]);

    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(getConfiguredAdditionalComponents());
    }, [dispatch]);

    const switchNode = useCallback(
        (selectedNode: NodeType) => {
            const { type, ...source } = node;

            const customizer = (arg1, arg2, key: string) => {
                if (key === "parameters" && isArray(arg1)) {
                    return arg1.map((parameter) => arg2.find(({ name }) => name === parameter.name) || parameter);
                }
            };

            const nextNode = defaultsDeep(
                { additionalFields: { virtualNode: creatorType } },
                mergeWith(cloneDeep(selectedNode), source, customizer),
            );

            return onChange(nextNode, NodeUtils.hasOutputs(nextNode, processDefinitionData) ? edges : []);
        },
        [creatorType, edges, node, onChange, processDefinitionData],
    );

    if (!creatorType) {
        return null;
    }

    return (
        <>
            {/* eslint-disable-next-line i18next/no-literal-string */}
            <h1>type: {creatorType}</h1>
            {componentsToSelect.map((c) => (
                <div key={c.componentId}>
                    {/* eslint-disable-next-line i18next/no-literal-string */}
                    <button onClick={() => switchNode(c.node)}>switch to {c.componentId}</button>
                </div>
            ))}
        </>
    );
}
