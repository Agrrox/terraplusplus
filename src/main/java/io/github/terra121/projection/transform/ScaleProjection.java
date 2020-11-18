package io.github.terra121.projection.transform;

import io.github.terra121.projection.GeographicProjection;

public class ScaleProjection extends AbstractTransformation {

    double scaleX;
    double scaleY;

    public ScaleProjection(GeographicProjection input, double scaleX, double scaleY) {
        super(input);
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public ScaleProjection(GeographicProjection input, double scale) {
        this(input, scale, scale);
    }

    @Override
    public double[] toGeo(double x, double y) {
        return this.input.toGeo(x / this.scaleX, y / this.scaleY);
    }

    @Override
    public double[] fromGeo(double lon, double lat) {
        double[] p = this.input.fromGeo(lon, lat);
        p[0] *= this.scaleX;
        p[1] *= this.scaleY;
        return p;
    }

    @Override
    public boolean upright() {
        return (this.scaleY < 0) ^ this.input.upright();
    }

    @Override
    public double[] bounds() {
        double[] b = this.input.bounds();
        b[0] *= this.scaleX;
        b[1] *= this.scaleY;
        b[2] *= this.scaleX;
        b[3] *= this.scaleY;
        return b;
    }

    @Override
    public double metersPerUnit() {
        return this.input.metersPerUnit() / Math.sqrt((this.scaleX * this.scaleX + this.scaleY * this.scaleY) / 2); //TODO: better transform
    }
}