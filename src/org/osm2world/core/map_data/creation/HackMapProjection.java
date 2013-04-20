package org.osm2world.core.map_data.creation;

import org.apache.commons.configuration.Configuration;
import org.openstreetmap.osmosis.core.domain.v0_6.Bound;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.osm.data.OSMData;
import org.osm2world.core.osm.data.OSMNode;

/**
 * quick-and-dirty projection that is intended to use the "dense" space
 * of floating point values, and tries to make 1 meter the distance
 * represented by 1 internal unit
 */
public class HackMapProjection implements MapProjection {

	static class MercatorProjection{
		
		public static final double LATITUDE_MAX = 85.05112877980659;
		public static final double LATITUDE_MIN = -LATITUDE_MAX;

    	public static double latitudeToY(double latitude) {
    		double sinLatitude = Math.sin(latitude * (Math.PI / 180));
    		return  360 * Math.log((1 + sinLatitude) / (1 - sinLatitude)) / (4 * Math.PI);
    		//return Math.log(Math.tan((Math.PI / 4) + 0.5 * latitude));
    	}

    	public static double toLatitude(double y) {
    		return 360 * -Math.atan(Math.exp((y - 0.5) * (2 * Math.PI))) / Math.PI;
    	}
    	
    	public static double longitudeToX(double longitude) {
    		return longitude;
    	}

    	public static double toLongitude(double x) {
    		return x;
    	}	
	}
	
	/**
	 * The coordinate origin is placed at the center of the bounds,
	 * or else at the first node's coordinates.
	 * All coordinates will be modified by subtracting the origin
	 * (in lat/lon, which does not really make sense, but is simply
	 *  supposed to keep nodes as close as possible to 0.0).
	 * 
	 * //TODO: replace this solution later
	 */
	
	private final Double originLat, originLon;

	public static final double EARTH_CIRCUMFERENCE_E = 40075016.686;
	public static final double EARTH_CIRCUMFERENCE_M = 40007860.000;
	
	public final double SCALE_X;
	public final double SCALE_Y;

	/* magic constant */
	//public static final double SCALE_X = 70000;
	//public static final double SCALE_Y = 110000;
	
	public HackMapProjection(Configuration config, OSMData osmData) {
		
		double latitude;
		double longitude;
		
		if (config.containsKey("MapCenterLat") && config.containsKey("MapCenterLon")){
			latitude = config.getDouble("MapCenterLat", 0);
			longitude = config.getDouble("MapCenterLon", 0);
			
		} else if (osmData.getBounds() != null && !osmData.getBounds().isEmpty()) {
			
			Bound firstBound = osmData.getBounds().iterator().next();
			latitude = (firstBound.getTop() + firstBound.getBottom()) / 2;
			longitude = (firstBound.getLeft() + firstBound.getRight()) / 2;
		} else {
			if (osmData.getNodes().isEmpty()) {
				throw new IllegalArgumentException("OSM data must contain nodes");
			}
			OSMNode firstNode = osmData.getNodes().iterator().next();
			latitude = firstNode.lat;
			longitude = firstNode.lon;
		}
		
		double groundResolution = Math.cos(latitude * (Math.PI / 180));
		
		SCALE_X = (groundResolution * EARTH_CIRCUMFERENCE_E) / 360;
		SCALE_Y = (groundResolution * EARTH_CIRCUMFERENCE_M) / 360;
		
		originLat = MercatorProjection.latitudeToY(latitude);
		originLon = MercatorProjection.longitudeToX(longitude);
	}

	public VectorXZ calcPos(double lat, double lon) {
		
		if (lat > MercatorProjection.LATITUDE_MAX || lat < MercatorProjection.LATITUDE_MIN){
			System.out.println("NaN!");
			return new VectorXZ(0, 0);
		}

		lon = MercatorProjection.longitudeToX(lon) - originLon;
		lat = MercatorProjection.latitudeToY(lat) - originLat;

		return new VectorXZ(lon * SCALE_X, lat * SCALE_Y);
		
	}
	
	@Override
	public VectorXZ calcPos(LatLon latlon) {
		return calcPos(latlon.lat, latlon.lon);
	}
	
	@Override
	public double calcLat(VectorXZ pos) {
		if (pos.equals(VectorXZ.NULL_VECTOR)) {
			return originLat;
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	@Override
	public double calcLon(VectorXZ pos) {
		if (pos.equals(VectorXZ.NULL_VECTOR)) {
			return originLon;
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	@Override
	public VectorXZ getNorthUnit() {
		return VectorXZ.Z_UNIT;
	}
	
}
