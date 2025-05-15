import { Box, Skeleton } from "@mui/material";
import React, { Fragment } from "react";

import type { Parameter } from "../../../types";
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
    const isDynamicParametersLoading = node.isLoading;

    console.log(parameterDefinitions);

    return (
        <>
            {parameters.map(
                (paramWithIndex) =>
                    console.log(paramWithIndex) || (
                        <Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                            {isDynamicParametersLoading ? (
                                <Box display={"flex"} justifyContent={"space-between"} mt={2}>
                                    <Skeleton variant="rectangular" height={15} width={"100%"} sx={{ flexBasis: "10%", mt: "9px" }} />
                                    <Skeleton variant="rectangular" height={35} width={"100%"} sx={{ flexBasis: "80%" }} />
                                </Box>
                            ) : (
                                <ParametersListField {...props} paramWithIndex={paramWithIndex} />
                            )}
                        </Fragment>
                    ),
            )}
        </>
    );
};
