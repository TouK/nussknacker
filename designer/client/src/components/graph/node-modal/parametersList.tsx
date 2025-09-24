import { Box, Skeleton } from "@mui/material";
import { isEqual } from "lodash";
import React, { Fragment } from "react";

import { useAppSelector } from "../../../store/storeHelpers";
import type { Parameter } from "../../../types/node";
import { getDynamicParametersChanged } from "./NodeDetailsContent/selectors";
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
    const { parameters = [], node } = props;
    const dynamicParametersChanged = useAppSelector(getDynamicParametersChanged, isEqual)(node.id);

    return (
        <>
            {parameters.map((paramWithIndex) => (
                <Fragment key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    {dynamicParametersChanged?.length > 0 && !dynamicParametersChanged.includes(paramWithIndex.param.name) ? (
                        <Box display={"flex"} justifyContent={"space-between"} mt={2} fontSize={14}>
                            <Box height={15} sx={{ flexBasis: "20%", mt: "9px", maxWidth: "20em" }}>
                                <Skeleton variant="rectangular" sx={{ maxWidth: "75px" }} />
                            </Box>
                            <Skeleton variant="rectangular" height={35} width={"100%"} sx={{ flexBasis: "60%", flex: 1 }} />
                        </Box>
                    ) : (
                        <ParametersListField {...props} paramWithIndex={paramWithIndex} />
                    )}
                </Fragment>
            ))}
        </>
    );
};
