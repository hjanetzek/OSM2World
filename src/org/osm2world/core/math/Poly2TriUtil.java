package org.osm2world.core.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.poly2tri.Poly2Tri;
import org.poly2tri.triangulation.Triangulatable;
import org.poly2tri.triangulation.TriangulationAlgorithm;
import org.poly2tri.triangulation.TriangulationContext;
import org.poly2tri.triangulation.TriangulationMode;
import org.poly2tri.triangulation.TriangulationPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import org.poly2tri.triangulation.point.TPoint;

public class Poly2TriUtil {
	static class CDTSet implements Triangulatable {
		List<TriangulationPoint> points = new ArrayList<TriangulationPoint>(20);
		List<DelaunayTriangle> triangles = new ArrayList<DelaunayTriangle>(20);

		ArrayList<LineSegmentXZ> segmentSet = new ArrayList<LineSegmentXZ>();

		public CDTSet(SimplePolygonXZ polygon,
				Collection<SimplePolygonXZ> holes,
				Collection<LineSegmentXZ> segments) {

			List<VectorXZ> vertices = polygon.getVertexLoop();

			for (int i = 0, n = vertices.size() - 1; i < n; i++)
				segmentSet.add(new LineSegmentXZ(vertices.get(i), vertices.get(i + 1)));
			
			segmentSet.addAll(segments);
			
			int size = segmentSet.size();
			
			for (int i = 0;i < size - 1; i++){
				LineSegmentXZ l1 = segmentSet.get(i);

				for (int j = i + 1; j < size; j++){
					LineSegmentXZ l2 = segmentSet.get(j);

					if ((l1.p1.x == l2.p1.x && l1.p1.z == l2.p1.z
							&& l1.p2.x == l2.p2.x && l1.p2.z == l2.p2.z)
							|| (l1.p1.x == l2.p2.x && l1.p1.z == l2.p2.z
									&& l1.p2.x == l2.p1.x && l1.p2.z == l2.p1.z)) {
						System.out.println("remove dup " + l1 + " " + l2);
						segmentSet.remove(j);
						size--;
					}
				}
			}
		}

		public TriangulationMode getTriangulationMode() {
			return TriangulationMode.CONSTRAINED;
		}

		public List<TriangulationPoint> getPoints() {
			return points;
		}

		public List<DelaunayTriangle> getTriangles() {
			return triangles;
		}

		public void addTriangle(DelaunayTriangle t) {
			triangles.add(t);
		}

		public void addTriangles(List<DelaunayTriangle> list) {
			triangles.addAll(list);
		}

		public void clearTriangulation() {
			triangles.clear();
		}

		public void prepareTriangulation(TriangulationContext<?> tcx) {
			triangles.clear();

			// need to make points unique objects, wtf?.. 
			HashMap<TriangulationPoint,TriangulationPoint> pointSet = 
					new HashMap<TriangulationPoint, TriangulationPoint>();
		
			for (LineSegmentXZ l : segmentSet){
				TriangulationPoint p1 = new TPoint(l.p1.x, l.p1.z);
				TriangulationPoint p2 = new TPoint(l.p2.x, l.p2.z);

				if (!pointSet.containsKey(p1))
					pointSet.put(p1,p1);
				else
					p1 = pointSet.get(p1);

				if (!pointSet.containsKey(p2))
					pointSet.put(p2,p2);
				else
					p2 = pointSet.get(p2);

				//System.out.println("add edge: " + p1 + " -> " + p2);
				tcx.newConstraint(p1, p2);
			}

			segmentSet.clear();
			
			points.addAll(pointSet.keySet());
			pointSet.clear();
			
			tcx.addPoints(points);
		}
	}

	public static final List<TriangleXZ> triangulate(SimplePolygonXZ polygon,
			Collection<SimplePolygonXZ> holes,
			Collection<LineSegmentXZ> segments, Collection<VectorXZ> points) {

		CDTSet cdt = new CDTSet(polygon, holes, segments);
		TriangulationContext<?> tcx = Poly2Tri
				.createContext(TriangulationAlgorithm.DTSweep);
		tcx.prepareTriangulation(cdt);

		Poly2Tri.triangulate(tcx);

		List<TriangleXZ> triangles = new ArrayList<TriangleXZ>();

		// List<DelaunayTriangle> result = tcx.getTriangles();
		List<DelaunayTriangle> result = cdt.getTriangles();

		if (result == null) {
			System.out.println("...... missing triangles ......");
			return triangles;
		}

		Collection<PolygonWithHolesXZ> trianglesAsPolygons = new ArrayList<PolygonWithHolesXZ>();

		for (DelaunayTriangle t : result) {
			List<VectorXZ> triVertices = new ArrayList<VectorXZ>(3);
			for (int i = 0; i < 3; i++)
				triVertices.add(new VectorXZ(t.points[i].getX(), t.points[i]
						.getY()));

			triVertices.add(triVertices.get(0));

			trianglesAsPolygons.add(new PolygonWithHolesXZ(new SimplePolygonXZ(
					triVertices), Collections.<SimplePolygonXZ> emptyList()));
		}

		for (PolygonWithHolesXZ triangleAsPolygon : trianglesAsPolygons) {

			boolean triangleInHole = false;
			for (SimplePolygonXZ hole : holes) {
				if (hole.contains(triangleAsPolygon.getOuter().getCenter())) {
					triangleInHole = true;
					break;
				}
			}

			if (!triangleInHole
					&& polygon.contains(triangleAsPolygon.getOuter()
							.getCenter())) { // TODO: create single method for
												// this query within
												// PolygonWithHoles

				triangles.add(triangleAsPolygon.asTriangleXZ());

			}

		}

		return triangles;

	}
}
