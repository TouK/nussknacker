import PropTypes from "prop-types";
import Input from "./Input";
import Textarea from "./Textarea";
import {v4 as uuid4} from "uuid";
import React from "react";
import _ from "lodash";
import Fields from "./Fields";

const MapVariable = (props) => {

    const {isMarked, node, removeElement, addElement, onChange, readOnly} = props;

    const addField = () => {
        addElement("fields", {"name": "", "uuid": uuid4(), "expression": {"expression":"", "language": "spel"}});
    };

    return (
        <div className="node-table-body node-variable-builder-body">
            <Input label="Id" value={node.id} path="id" onChange={onChange} isMarked={isMarked("id")} readOnly={readOnly} />
            <Input label="Variable Name" placeholder="value" value={node.varName} path="varName" onChange={onChange} isMarked={isMarked("varName")} readOnly={readOnly} />
            <Fields
                label="Fields"
                onChange={onChange}
                fields={node.fields}
                removeField={removeElement}
                namespace="fields"
                addField={addField}
                isMarked={isMarked}
            />
            <Textarea
                label="Description"
                value={_.get(props.node, "additionalFields.description", "")}
                path="additionalFields.description"
                onChange={onChange}
                isMarked={isMarked("additionalFields.description")}
                readOnly={readOnly}
            />
        </div>
    );
};

MapVariable.propTypes = {
    isMarked: PropTypes.func.isRequired,
    node: PropTypes.object.isRequired,
    removeElement: PropTypes.func.isRequired,
    addElement: PropTypes.func.isRequired,
    onChange: PropTypes.func.isRequired,
    readOnly: PropTypes.bool
};

MapVariable.defaultProps = {
    readOnly: false
};

export default MapVariable;