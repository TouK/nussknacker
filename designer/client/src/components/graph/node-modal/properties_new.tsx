import { useSelector } from "react-redux";
import { getProcessName, getScenarioPropertiesConfig } from "./NodeDetailsContent/selectors";
import React, { useMemo, useState } from "react";
import { sortBy } from "lodash";
import { set } from "lodash/fp";
import { IdField } from "./IdField";
import ScenarioProperty from "./ScenarioProperty";
import { DescriptionField } from "./DescriptionField";
import { FieldLabel } from "./FieldLabel";
import { FieldType } from "./editors/field/Field";
import { NodeField } from "./NodeField";
import { getPropertiesErrors } from "./node/selectors";
import { NodeTable } from "./NodeDetailsContent/NodeTable";
import { css } from "@emotion/css";
import { ContentSize } from "./node/ContentSize";
import NodeUtils from "../NodeUtils";
import { getProcessUnsavedNewName, getScenario } from "../../../reducers/selectors/graph";
import HttpService from "../../../http/HttpService";

interface Props {
    isEditMode: boolean;
}

export function PropertiesNew({ isEditMode }: Props): JSX.Element {
    const errors = useSelector(getPropertiesErrors);

    console.log("errors", errors);

    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = useMemo(() => scenarioProperties?.propertiesConfig ?? {}, [scenarioProperties?.propertiesConfig]);

    //fixme move this configuration to some better place?
    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    const scenario = useSelector(getScenario);
    const scenarioName = useSelector(getProcessName);
    const name = useSelector(getProcessUnsavedNewName);

    const [node, setNode] = useState(() => NodeUtils.getProcessPropertiesNode(scenario, name));

    const setProperty = (label: string | number, value: string) => {
        setNode((prevState) => set<typeof node>(label, value, prevState) as unknown as typeof node);
        HttpService.validateProperties(scenarioName, { additionalFields: node.additionalFields, name: node.id }).then((data) => {
            console.log("data", data);
        });
    };

    const showValidation = false;

    const showSwitch = false;

    return (
        <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
            <ContentSize>
                <NodeTable>
                    <IdField
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        node={node}
                        renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                        setProperty={setProperty}
                        errors={errors}
                    />
                    {scenarioPropertiesSorted.map(([propName, propConfig]) => (
                        <ScenarioProperty
                            key={propName}
                            showSwitch={showSwitch}
                            showValidation={showValidation}
                            propertyName={propName}
                            propertyConfig={propConfig}
                            errors={errors}
                            onChange={setProperty}
                            renderFieldLabel={() => (
                                <FieldLabel title={propConfig.label} label={propConfig.label} hintText={propConfig.hintText} />
                            )}
                            editedNode={node}
                            readOnly={!isEditMode}
                        />
                    ))}
                    <DescriptionField
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        node={node}
                        renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                        setProperty={setProperty}
                        errors={errors}
                    />
                    <NodeField
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        node={node}
                        renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                        setProperty={setProperty}
                        errors={errors}
                        fieldType={FieldType.checkbox}
                        fieldName={"additionalFields.showDescription"}
                        description={"Show description each time scenario is opened"}
                    />
                </NodeTable>
            </ContentSize>
        </div>
    );
}
