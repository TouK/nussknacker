import { defaultsDeep } from "lodash";
import React, { useCallback, useEffect, useMemo } from "react";
import { useDispatch, useSelector } from "react-redux";
import { getConfiguredAdditionalComponents } from "../../../reducers/cloudData";
import { getCreatorType } from "../../../reducers/selectors/getCreator";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeGroupContentProps } from "./node/NodeGroupContent";

type NodeSwitcherProps = NodeGroupContentProps & {
    componentsNamesToSelect: string[];
};

export function NodeSwitcher({ node, onChange, edges, componentsNamesToSelect = [] }: NodeSwitcherProps) {
    const { componentGroups } = useSelector(getProcessDefinitionData);

    const componentsToSelect = useMemo(() => {
        return componentGroups.flatMap((g) => g.components).filter((c) => componentsNamesToSelect.includes(c.componentId));
    }, [componentGroups, componentsNamesToSelect]);

    const creatorType = useMemo(() => getCreatorType(node), [node]);

    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(getConfiguredAdditionalComponents());
    }, [dispatch]);

    const switchNode = useCallback(
        (component) => {
            onChange(
                defaultsDeep(
                    {
                        id: node.id,
                        additionalFields: {
                            virtualNode: creatorType,
                        },
                    },
                    component.node,
                    node,
                ),
                edges,
            );
        },
        [creatorType, edges, node, onChange],
    );

    return (
        <>
            {/* eslint-disable-next-line i18next/no-literal-string */}
            <h1>type: {creatorType}</h1>
            {componentsToSelect.map((c) => (
                <div key={c.componentId}>
                    {/* eslint-disable-next-line i18next/no-literal-string */}
                    <button onClick={() => switchNode(c)}>switch to {c.componentId}</button>
                </div>
            ))}
        </>
    );
}
