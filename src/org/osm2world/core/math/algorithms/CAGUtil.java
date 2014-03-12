package org.osm2world.core.math.algorithms;

import static org.osm2world.core.math.JTSConversionUtil.polygonXZToJTSPolygon;
import static org.osm2world.core.math.JTSConversionUtil.polygonsXZFromJTSGeometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.osm2world.core.math.InvalidGeometryException;
import org.osm2world.core.math.PolygonWithHolesXZ;
import org.osm2world.core.math.SimplePolygonXZ;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.operation.overlay.snap.GeometrySnapper;

/**
 * utility class for Constructive Area Geometry (CAG),
 * boolean operations on areas
 */
public final class CAGUtil {
	
	private CAGUtil() { }
	
	/**
	 * takes a polygon outline, "subtracts" a collection of other polygon outlines, 
	 * and returns a collection of polygons that covers the difference area.
	 * 
	 * The result polygons should cover the area that was
	 * within the original polygon (excluding its holes),
	 * but not within a subtracted polygon.
	 * 
	 * @return
	 * 	 polygons without self-intersections, but maybe with holes
	 */
	public static final Collection<PolygonWithHolesXZ> subtractPolygons(
			SimplePolygonXZ basePolygon,
			List<? extends SimplePolygonXZ> subtractPolygons) {
		
		List<Geometry> remainingGeometry = Collections.singletonList(
				(Geometry)polygonXZToJTSPolygon(basePolygon));
		
		for (SimplePolygonXZ subtractPolygon : subtractPolygons) {
			
			List<Geometry> newRemainingGeometry = new ArrayList<Geometry>(1);
			
			for (Geometry g : remainingGeometry) {
				Polygon p = polygonXZToJTSPolygon(subtractPolygon);
				
				Geometry newG = null;
				try {
					newG = g.difference(p);
				} catch (TopologyException e) {
					System.out.println("subtract poly test: " + e.getMessage());
					try {
						// g = g.buffer(0);
						// p = (Polygon) p.buffer(0);

						// p = (Polygon) (new GeometrySnapper(p).snapTo(g,
						// 0.01));
						// newG = g.difference(p);
						Geometry[] out = GeometrySnapper.snap(g.buffer(0),
								p.buffer(0), 0.001);
						newG = out[0].difference(out[1]);
					} catch (Exception ee) {
						System.out.println("subtract poly fail: "
								+ ee.getMessage());
						continue;
					}
				}
				
				if (newG instanceof GeometryCollection) {
					GeometryCollection c = (GeometryCollection) newG;
					for (int i = 0; i < c.getNumGeometries(); i++) {
						newRemainingGeometry.add(c.getGeometryN(i));
					}
				} else {
					newRemainingGeometry.add(newG);
				}
				
			}
			
			remainingGeometry = newRemainingGeometry;
						
		}
				
		Collection<PolygonWithHolesXZ> result =
			new ArrayList<PolygonWithHolesXZ>();
		
		for (Geometry g : remainingGeometry) {
			try {
			result.addAll(polygonsXZFromJTSGeometry(g));
			} catch (InvalidGeometryException e) {
				System.err.println("error: " + e.getMessage());
			}
		}
		
		return result;
		
	}

	/**
	 * calculates the intersection area of a collection of polygons.
	 * 
	 * The result polygons should cover the area that was
	 * within all of the polygons.
	 */
	public static final Collection<PolygonWithHolesXZ> intersectPolygons(
			List<? extends SimplePolygonXZ> intersectPolygons) {
		
		if (intersectPolygons.isEmpty()) { throw new IllegalArgumentException(); }
				
		Geometry remainingGeometry = null;
		
		for (SimplePolygonXZ poly : intersectPolygons) {
			
			Polygon jtsPoly = polygonXZToJTSPolygon(poly);
			
			if (remainingGeometry == null) {
				remainingGeometry = jtsPoly;
			} else {
				remainingGeometry = remainingGeometry.intersection(jtsPoly);
			}
			
		}
		
		return polygonsXZFromJTSGeometry(remainingGeometry);
				
	}
	
}
