import React from "react";

import HttpService from "../../../http/HttpService/instance";
import type { NodeType, PropertiesType } from "../../../types/node";
import { usePropertiesState } from "../../modals/usePropertiesState";
import AdditionalInfoBox from "./AdditionalInfoBox";
import { ForEachAdditionalInfo } from "./ForEachAdditionalInfo";
import { nodeMatchesOverrideKey, OverrideKeys } from "./parameterHelpers";
import { isRequestSource } from "./requestSourceAddons";

export const PropertiesAdditionalInfo = () => {
    const { editedProperties } = usePropertiesState();
    return <AdditionalInfoBox node={editedProperties} handleGetAdditionalInfo={HttpService.getPropertiesAdditionalInfo} />;
};

export function NodeAdditionalInfo({ node }: { node: NodeType }) {
    if (isRequestSource(node)) {
        return <PropertiesAdditionalInfo />;
    }

    if (nodeMatchesOverrideKey(node, OverrideKeys.ForEachElements)) {
        return <ForEachAdditionalInfo />;
    }

    return <AdditionalInfoBox node={node} handleGetAdditionalInfo={HttpService.getNodeAdditionalInfo} />;
}
