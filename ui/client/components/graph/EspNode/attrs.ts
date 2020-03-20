import { edgeStroke, nodeLabelFontSize, rectHeight, rectWidth } from './misc';

export const attrsConfig = () => {
    return {
        '.': {
            magnet: false,
        },
        '.body': {
            fill: 'none',
            width: rectWidth,
            height: rectHeight,
            stroke: '#B5B5B5',
            strokeWidth: 1,
        },
        '.background': {
            width: rectWidth,
            height: rectHeight,
        },
        '.disabled-node-layer': {
            width: rectWidth,
            height: rectHeight,
            zIndex: 0,
        },
        text: {
            fill: '#1E1E1E',
            pointerEvents: 'none',
            fontWeight: 400,
        },
        '.nodeIconPlaceholder': {
            x: 0,
            y: 0,
            height: rectHeight,
            width: rectHeight,
        },
        '.nodeIconItself': {
            width: rectHeight / 2,
            height: rectHeight / 2,
            ref: '.nodeIconPlaceholder',
            refX: rectHeight / 4,
            refY: rectHeight / 4,
        },
        '.contentText': {
            fontSize: nodeLabelFontSize,
            ref: '.nodeIconPlaceholder',
            refX: rectHeight + 10,
            refY: rectHeight / 2,
            textVerticalAnchor: 'middle',
        },
        '.testResultsPlaceholder': {
            ref: '.nodeIconPlaceholder',
            refX: rectWidth,
            y: 0,
            height: rectHeight,
            width: rectHeight,
        },
        '.testResultsSummary': {
            textAnchor: 'middle',
            alignmentBaseline: 'middle',
        },
    };
};
const portsAttrs = () => {
    return {
        '.port': {
            refX: 0,
            refY: 0,
        },
        '.port-body': {
            r: 5,
            magnet: true,
            fontSize: 10,
        },
    };
};
export const portInAttrs = () => {
    return Object.assign({}, portsAttrs(), {
        '.port circle': {
            fill: '#FFFFFF',
            magnet: 'passive',
            stroke: edgeStroke,
            strokeWidth: '1',
            type: 'input',
        },
    });
};
export const portOutAttrs = () => {
    return Object.assign({}, portsAttrs(), {
        '.port circle': {
            fill: '#FFFFFF',
            stroke: edgeStroke,
            strokeWidth: '1',
            type: 'output',
        },
    });
};
