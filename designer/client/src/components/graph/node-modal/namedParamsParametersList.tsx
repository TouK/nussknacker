import type { PropsWithChildren } from "react";
import React, { useCallback, useState } from "react";

import { isDataMapper } from "../../../common/componentUtils";
import { NamedParamsDataMapper } from "./NamedParamsDataMapper";
import { NamedParamsMapperContext } from "./NamedParamsMapperContext";
import type { AdvancedParametersListProps } from "./parametersListAdvanced";
import { ParametersListAdvanced } from "./parametersListAdvanced";

type NamedParamsParametersListProps = Omit<PropsWithChildren<AdvancedParametersListProps>, "FieldWrapper">;

export const NamedParamsParametersList = ({ children, ...props }: NamedParamsParametersListProps) => {
    const { node, parameterDefinitions, setProperty } = props;
    const showDataMapper = isDataMapper(node, parameterDefinitions);

    const [mapperOpen, setMapperOpen] = useState(false);
    const [mapperFocusField, setMapperFocusField] = useState<string | undefined>();
    const openMapper = useCallback((fieldName: string) => {
        setMapperFocusField(fieldName);
        setMapperOpen(true);
    }, []);

    return (
        <NamedParamsMapperContext.Provider value={showDataMapper ? openMapper : null}>
            <ParametersListAdvanced {...props}>
                {showDataMapper && (
                    <NamedParamsDataMapper
                        node={node}
                        parameterDefinitions={parameterDefinitions}
                        setProperty={setProperty}
                        open={mapperOpen}
                        focusFieldName={mapperFocusField}
                        onClose={() => setMapperOpen(false)}
                    />
                )}
                {children}
            </ParametersListAdvanced>
        </NamedParamsMapperContext.Provider>
    );
};
