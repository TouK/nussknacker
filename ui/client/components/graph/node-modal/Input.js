import PropTypes from "prop-types";
import React from "react";
import {notEmptyValidator} from "../../../common/Validators";
import {v4 as uuid4} from "uuid";

const Input = (props) => {
    const {label, path, value, onChange, isMarked, readOnly, validators} = props;

    return (
        <div className="node-row">
            <div className="node-label" title={label}>{label}:</div>
            <div className={"node-value" + (isMarked ? " marked" : "")}>
                <input
                    key={label}
                    type="text"
                    className="node-input"
                    value={value}
                    onChange={(event) => onChange(path, event.target.value)}
                    readOnly={readOnly}
                />
                {
                    validators.map(validator =>
                      validator.isValid(value) ? null : <label key={label + uuid4()} className='node-details-validation-label'>{notEmptyValidator.message}</label>)
                }
            </div>
        </div>
    );
};

Input.propTypes = {
    isMarked: PropTypes.bool,
    readOnly: PropTypes.bool,
    label: PropTypes.string.isRequired,
    value: PropTypes.string.isRequired,
    path: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired
};

Input.defaultProps = {
    isMarked: false,
    readOnly: false,
};

export default Input;