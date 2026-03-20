import React, { useContext, useMemo } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import { EditorType } from "./editors/expression/types";
import { NamedParamsMapperContext } from "./NamedParamsMapperContext";
import { BuilderIconButton } from "./node-action-buttons/StyledLoadingButton";
import type { FieldWrapperProps } from "./ParameterExpressionField";

export function NamedParamsMapperFieldWrapper({ children, parameter, parameterDefinitions, isEditMode }: FieldWrapperProps) {
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const namedParamsMapperContext = useContext(NamedParamsMapperContext);

    const paramDef = useMemo(() => parameterDefinitions?.find((p) => p.name === parameter.name), [parameter.name, parameterDefinitions]);

    const isMappable = useMemo(
        () =>
            showDataMapper &&
            isEditMode &&
            namedParamsMapperContext &&
            !paramDef?.changesCanReloadParameters &&
            paramDef?.editors?.some((e) => e.type === EditorType.SPEL_PARAMETER_EDITOR),
        [isEditMode, namedParamsMapperContext, paramDef?.changesCanReloadParameters, paramDef?.editors, showDataMapper],
    );

    return (
        <>
            {isMappable
                ? React.cloneElement(children as React.ReactElement, {
                      inputAdornmentEnd: <BuilderIconButton onClick={() => namedParamsMapperContext(parameter.name)} label="Data Mapper" />,
                  })
                : children}
        </>
    );
}
