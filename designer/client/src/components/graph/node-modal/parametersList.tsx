import { Box, Skeleton } from "@mui/material";
import React, { Fragment } from "react";
import { useSelector } from "react-redux";

import type { Parameter } from "../../../types";
import { getNodeDetails } from "./NodeDetailsContent/selectors";
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

export const ParametersList = ({ parameters = [], getListFieldPath, ...props }: ParametersListProps) => {
    const { node } = props;
    const nodeDetails = useSelector(getNodeDetails);
    const isDynamicParametersLoading = nodeDetails(node.id)?.loading;

    console.log(parameters);
    return (
        <>
            {parameters.map((paramWithIndex) => (
                <Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    {paramWithIndex.param.name === "Endpoint" ? (
                        <ParameterExpressionField
                            listFieldPath={getListFieldPath(paramWithIndex.index)}
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
                                    listFieldPath={getListFieldPath(paramWithIndex.index)}
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
