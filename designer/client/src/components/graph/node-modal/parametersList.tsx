import { Box, Skeleton } from "@mui/material";
import React, { Fragment, useCallback } from "react";

import type { Parameter } from "../../../types";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import { ParameterExpressionField } from "./ParameterExpressionField";

type ParametersListItemProps = Omit<ParameterExpressionFieldProps, "listFieldPath" | "parameter">;

export type ParameterWithIndex = {
    index: number;
    param: Parameter;
};

export type ParametersListProps = ParametersListItemProps & {
    parameters: ParameterWithIndex[];
    getListFieldPath: (index: number) => string;
};

export const ParametersList = (ppp: ParametersListProps) => {
    const { parameters = [], getListFieldPath, ...props } = ppp;
    const { node } = props;
    const isDynamicParametersLoading = node.isLoading;

    const handleGetListFieldPath = useCallback(
        (index: number) => {
            return getListFieldPath(index);
        },
        [getListFieldPath],
    );

    return (
        <>
            {parameters.map((paramWithIndex) => (
                <Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    {paramWithIndex.param.name === "Endpoint" ? (
                        <ParameterExpressionField
                            listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                            parameter={paramWithIndex.param}
                            {...props}
                        />
                    ) : (
                        <>
                            {isDynamicParametersLoading ? (
                                <Box display={"flex"} justifyContent={"space-between"}>
                                    <Skeleton variant="rectangular" height={15} width={"100%"} sx={{ flexBasis: "10%", mt: "9px" }} />
                                    <Skeleton variant="rectangular" height={35} width={"100%"} sx={{ mb: 2, flexBasis: "80%" }} />
                                </Box>
                            ) : (
                                <ParameterExpressionField
                                    listFieldPath={handleGetListFieldPath(paramWithIndex.index)}
                                    parameter={paramWithIndex.param}
                                    {...props}
                                />
                            )}
                        </>
                    )}
                </Fragment>
            ))}
        </>
    );
};
