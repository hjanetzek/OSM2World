package org.osm2world.core.math.algorithms;

import org.osm2world.core.math.JTSConversionUtil;
import org.osm2world.core.math.PolygonWithHolesXZ;

import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier;

public class JTSSimplifyUtil {

	public static PolygonWithHolesXZ simplifyPreserveTopology(
			PolygonWithHolesXZ polygon, double distanceTolerance) {
		
		PolygonWithHolesXZ simplified = polygon;
		try {
			Polygon jtsPoly = JTSConversionUtil.polygonXZToJTSPolygon(polygon);

			jtsPoly = (Polygon) TopologyPreservingSimplifier.simplify(jtsPoly,
					distanceTolerance);

			simplified = JTSConversionUtil.polygonXZFromJTSPolygon(jtsPoly);
		} catch (TopologyException e) {
			System.out.println(" JTSSimplifyUtil.simplifyPreserveTopology " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return simplified;
	}
}
