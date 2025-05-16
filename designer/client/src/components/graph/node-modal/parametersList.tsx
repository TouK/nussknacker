import { Box, Skeleton } from "@mui/material";
import { isEqual } from "lodash";
import React, { Fragment, useCallback } from "react";
import { useSelector } from "react-redux";

import type { Parameter } from "../../../types";
import { getNodeDetails } from "./NodeDetailsContent/selectors";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import { ParametersListField } from "./parametersListField";

type ParametersListItemProps = Omit<ParameterExpressionFieldProps, "listFieldPath" | "parameter">;

export type ParameterWithIndex = {
    index: number;
    param: Parameter;
};

export type ParametersListProps = ParametersListItemProps & {
    parameters: ParameterWithIndex[];
    getListFieldPath: (index: number) => string;
};

export const ParametersList = (props: ParametersListProps) => {
    const { parameters = [], node, parameterDefinitions } = props;
    const nodeDetails = useSelector(getNodeDetails, isEqual);
    const nodeDetail = nodeDetails(node.id);
    const isDynamicParametersLoading = nodeDetail?.isDynamicParametersLoading;

    console.log(parameters[0]);
    const getParameterDefinition = useCallback(
        (name: string) => parameterDefinitions.find((parameterDefinition) => parameterDefinition.name === name),
        [parameterDefinitions],
    );
    return (
        <>
            {parameters.map((paramWithIndex) => (
                <Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    {isDynamicParametersLoading && !getParameterDefinition(paramWithIndex.param.name).changesCanReloadParameters ? (
                        <Box display={"flex"} justifyContent={"space-between"} mt={2}>
                            <Skeleton variant="rectangular" height={15} width={"100%"} sx={{ flexBasis: "10%", mt: "9px" }} />
                            <Skeleton variant="rectangular" height={35} width={"100%"} sx={{ flexBasis: "80%" }} />
                        </Box>
                    ) : (
                        <ParametersListField {...props} paramWithIndex={paramWithIndex} />
                    )}
                </Fragment>
            ))}
        </>
    );
};
