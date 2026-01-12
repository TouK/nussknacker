import { Box, Skeleton } from "@mui/material";
import React from "react";

import { useAppSelector } from "../../../store/storeHelpers";
import type { Parameter } from "../../../types/node";
import { ParamKeyProvider } from "./editors/ParamKeyProvider";
import { getIsParameterStable } from "./NodeDetailsContent/selectors";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import type { ParametersListFieldProps } from "./parametersListField";
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

function ListElement(props: ParametersListFieldProps) {
    const { node, paramWithIndex } = props;
    const isParameterStable = useAppSelector((state) => {
        return getIsParameterStable(state, { node, param: paramWithIndex.param });
    });

    if (isParameterStable) {
        return <ParametersListField {...props} paramWithIndex={paramWithIndex} />;
    }

    return (
        <Box display={"flex"} justifyContent={"space-between"} mt={2} fontSize={14}>
            <Box height={15} sx={{ flexBasis: "20%", mt: "9px", maxWidth: "20em" }}>
                <Skeleton variant="rectangular" sx={{ maxWidth: "75px" }} />
            </Box>
            <Skeleton variant="rectangular" height={35} width={"100%"} sx={{ flexBasis: "60%", flex: 1 }} />
        </Box>
    );
}

export const ParametersList = (props: ParametersListProps) => {
    const { parameters = [], node } = props;
    return (
        <>
            {parameters.map((paramWithIndex) => (
                <ParamKeyProvider node={node} param={paramWithIndex.param} key={node.id + paramWithIndex.param.name + paramWithIndex.index}>
                    <ListElement paramWithIndex={paramWithIndex} {...props} />
                </ParamKeyProvider>
            ))}
        </>
    );
};
