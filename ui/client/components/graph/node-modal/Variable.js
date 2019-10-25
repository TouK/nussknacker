import PropTypes from "prop-types";
import Input from "./Input";
import Textarea from "./Textarea";
import ExpressionInput from "./ExpressionInput";
import React from "react";
import _ from "lodash";

const Variable = (props) => {

    const {node, onChange, isMarked, readOnly, placeholder} = props;

    return (
        <div className="node-table-body node-variable-builder-body">
            <Input label="Id" value={node.id} path="id" onChange={onChange} isMarked={isMarked("id")} readOnly={readOnly} />
            <Input label="Variable name" value={node.varName} path="varName" onChange={onChange} isMarked={isMarked("varName")} readOnly={readOnly} />
            <ExpressionInput name="expression" label="Expression" placeholder={placeholder} value={node.value} path="value.expression" onChange={onChange} readOnly={readOnly} />
            <Textarea
                label="Description"
                value={_.get(node, "additionalFields.description", "")}
                path="additionalFields.description"
                onChange={props.onChange}
                isMarked={isMarked("additionalFields.description")}
                readOnly={readOnly}
            />
        </div>
    );
};

Variable.propTypes = {
    readOnly: PropTypes.bool,
    isMarked: PropTypes.func.isRequired,
    node: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired
};

Variable.defaultProps = {
    readOnly: false
};

export default Variable;