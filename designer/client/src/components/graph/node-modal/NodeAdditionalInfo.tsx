import React from "react";

import HttpService from "../../../http/HttpService/instance";
import type { NodeType } from "../../../types/node";
import { usePropertiesState } from "../../modals/usePropertiesState";
import AdditionalInfoBox from "./AdditionalInfoBox";
import { isRequestSource } from "./requestSourceAddons";

export const PropertiesAdditionalInfo = () => {
    const { editedProperties } = usePropertiesState();
    return <AdditionalInfoBox node={editedProperties} handleGetAdditionalInfo={HttpService.getPropertiesAdditionalInfo} />;
};

export function NodeAdditionalInfo({ node }: { node: NodeType }) {
    return isRequestSource(node) ? (
        <PropertiesAdditionalInfo />
    ) : (
        <AdditionalInfoBox node={node} handleGetAdditionalInfo={HttpService.getNodeAdditionalInfo} />
    );
}
