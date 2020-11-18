package io.github.terra121.projection.transform;

import io.github.terra121.projection.GeographicProjection;

public class UprightOrientation extends AbstractTransformation {

    public UprightOrientation(GeographicProjection input) {
        super(input);
    }

    @Override
    public double[] toGeo(double x, double y) {
        return this.input.toGeo(x, -y);
    }

    @Override
    public double[] fromGeo(double lon, double lat) {
        double[] p = this.input.fromGeo(lon, lat);
        p[1] = -p[1];
        return p;
    }

    @Override
    public boolean upright() {
        return !this.input.upright();
    }

    @Override
    public double[] bounds() {
        double[] b = this.input.bounds();
        return new double[]{ b[0], -b[3], b[2], -b[1] };
    }
}