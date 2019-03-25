import PropTypes from "prop-types";
import React from "react";

const Textarea = (props) => {

    const {label, path, value, onChange, isMarked, readOnly, rows, cols} = props;

    return (
        <div className="node-row">
            <div className="node-label" title={label}>{label}:</div>
            <div className={"node-value" + (isMarked ? " marked" : "")}>
                <textarea
                    rows={rows}
                    cols={cols}
                    className="node-input"
                    value={value}
                    onChange={(event) => onChange(path, event.target.value)}
                    readOnly={readOnly}
                />
            </div>
        </div>
    );
};

Textarea.propTypes = {
    isMarked: PropTypes.bool,
    readOnly: PropTypes.bool,
    rows: PropTypes.number,
    cols: PropTypes.number,
    label: PropTypes.string.isRequired,
    value: PropTypes.string.isRequired,
    path: PropTypes.string.isRequired,
    onChange: PropTypes.func.isRequired
};

Textarea.defaultProps = {
    isMarked: false,
    readOnly: false,
    rows: 1,
    cols: 50,
};

export default Textarea;