package org.osm2world.core.map_data.creation;

import org.osm2world.core.math.VectorXZ;

import com.vividsolutions.jts.geom.Envelope;

public class TileProjection extends OriginMapProjection {

	private double originX;
	private double originY;
	private double groundScale;
	private final Envelope bbox;

	public TileProjection(Envelope bbox) {

		originX = (lonToX(bbox.getMaxX()) + lonToX(bbox.getMinX())) / 2;
		originY = (latToY(bbox.getMaxY()) + latToY(bbox.getMinY())) / 2;
		groundScale = groundResolution((bbox.getMinY() + bbox.getMaxY()) / 2);

		originX *= groundScale;
		originY *= groundScale;
		this.bbox = bbox;
	}

	public double getTileScale() {
		return groundScale * Math.abs(lonToX(bbox.getMaxX()) - lonToX(bbox.getMinX()));
	}

	public VectorXZ calcPos(double lat, double lon) {

		double x = lonToX(lon) * groundScale - originX;
		double y = latToY(lat) * groundScale - originY;

		/* snap to som cm precision */
		x = Math.round(x * 1000) / 1000.0d;
		y = Math.round(y * 1000) / 1000.0d;

		return new VectorXZ(x, y); // x and z(!) are 2d here
	}

	@Override
	public VectorXZ calcPos(LatLon latlon) {
		return calcPos(latlon.lat, latlon.lon);
	}

	@Override
	public double calcLat(VectorXZ pos) {
		return yToLat(pos.z + originY);
	}

	@Override
	public double calcLon(VectorXZ pos) {
		return xToLon(pos.x + originX);
	}

	@Override
	public VectorXZ getNorthUnit() {
		return VectorXZ.Z_UNIT;
	}

	@Override
	public void setOrigin(LatLon origin) {
		// TODO?
	}

	public static double lonToX(double lon) {
		return (lon + 180.0) / 360.0;
	}

	public static double xToLon(double x) {
		return 360.0 * (x - 0.5);
	}

	public static final double LATITUDE_MAX = 85.05112877980659;
	public static final double LATITUDE_MIN = -LATITUDE_MAX;

	public static double latToY(double latitude) {
		double sinLatitude = Math.sin(latitude * (Math.PI / 180));
		return Math.log((1 + sinLatitude) / (1 - sinLatitude)) / (4 * Math.PI);
	}

	public static final double EARTH_CIRCUMFERENCE = 40075016.686;

	public static double groundResolution(double latitude) {
		return EARTH_CIRCUMFERENCE * Math.cos(latitude * (Math.PI / 180));
	}

	public static double yToLat(double y) {
		return 90 - 360 * Math.atan(Math.exp((y - 0.5) * (2 * Math.PI)))
				/ Math.PI;
	}

}
