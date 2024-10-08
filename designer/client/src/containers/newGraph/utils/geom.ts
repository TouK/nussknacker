import { g } from "jointjs";
import { difference, Polygon, Ring } from "polygon-clipping";

function gPolygonToPolygon(polygon: g.Polygon): Polygon {
    return [polygon.points.map((p) => [p.x, p.y])];
}

function ringToGPolygon(ring: Ring): g.Polygon {
    return new g.Polygon(ring.map(([x, y]) => ({ x, y })));
}

export function polygonDiff(base: g.Polygon, cutout: g.Polygon): g.Polygon {
    const multipolygon = difference(gPolygonToPolygon(base), gPolygonToPolygon(cutout));
    if (multipolygon.length !== 1) {
        return base;
    }
    return ringToGPolygon(multipolygon[0][0]);
}
