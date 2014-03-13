package org.osm2world.core.target.obj;

import static java.awt.Color.WHITE;
import static java.lang.Math.max;
import static java.util.Collections.nCopies;
import static org.osm2world.core.target.common.material.Material.multiplyColor;

import java.awt.Color;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osm2world.core.map_data.creation.MapProjection;
import org.osm2world.core.map_data.creation.TileProjection;
import org.osm2world.core.map_data.data.MapArea;
import org.osm2world.core.map_data.data.MapElement;
import org.osm2world.core.map_data.data.MapNode;
import org.osm2world.core.map_data.data.MapWaySegment;
import org.osm2world.core.math.TriangleXYZ;
import org.osm2world.core.math.TriangleXYZWithNormals;
import org.osm2world.core.math.VectorXYZ;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.osm.data.OSMElement;
import org.osm2world.core.target.common.FaceTarget;
import org.osm2world.core.target.common.TextureData;
import org.osm2world.core.target.common.material.Material;
import org.osm2world.core.target.common.material.Materials;
import org.osm2world.core.world.data.WorldObject;

import com.google.common.collect.ImmutableList;

public class ObjTarget extends FaceTarget<RenderableToObj> {

	private final PrintStream objStream;
	private final PrintStream mtlStream;

	private final Map<VectorXYZ, Integer> vertexIndexMap = new HashMap<VectorXYZ, Integer>();
	private final Map<VectorXYZ, Integer> normalsIndexMap = new HashMap<VectorXYZ, Integer>();
	private final Map<VectorXZ, Integer> texCoordsIndexMap = new HashMap<VectorXZ, Integer>();
	private final Map<Material, String> materialMap = new HashMap<Material, String>();

	private Class<? extends WorldObject> currentWOGroup = null;
	private int anonymousWOCounter = 0;

	private Material currentMaterial = null;
	private int currentMaterialLayer = 0;
	private static int anonymousMaterialCounter = 0;

	// this is approximatly one millimeter
	private static final double SMALL_OFFSET = 1e-3;

	private boolean scaleCoordinates;
	private double coordinateScale;

	public ObjTarget(PrintStream objStream, PrintStream mtlStream, MapProjection projection) {

		this.objStream = objStream;
		this.mtlStream = mtlStream;
		if (projection instanceof TileProjection) {
			scaleCoordinates = true;
			coordinateScale = ((TileProjection) projection).getTileScale();
		}
	}

	@Override
	public Class<RenderableToObj> getRenderableType() {
		return RenderableToObj.class;
	}

	@Override
	public void render(RenderableToObj renderable) {
		renderable.renderTo(this);
	}

	@Override
	public boolean reconstructFaces() {
		return config != null && config.getBoolean("reconstructFaces", false);
	}

	@Override
	public void beginObject(WorldObject object) {

		if (object == null) {

			currentWOGroup = null;
			objStream.println("g null");
			objStream.println("o null");

		} else {

			/* maybe start a group depending on the object's class */

			if (!object.getClass().equals(currentWOGroup)) {
				currentWOGroup = object.getClass();
				objStream.println("g " + currentWOGroup.getSimpleName());
			}

			/*
			 * start an object with the object's class and the underlying OSM
			 * element's name/ref tags
			 */

			MapElement element = object.getPrimaryMapElement();
			OSMElement osmElement;
			if (element instanceof MapNode) {
				osmElement = ((MapNode) element).getOsmNode();
			} else if (element instanceof MapWaySegment) {
				osmElement = ((MapWaySegment) element).getOsmWay();
			} else if (element instanceof MapArea) {
				osmElement = ((MapArea) element).getOsmObject();
			} else {
				osmElement = null;
			}

			if (osmElement != null && osmElement.tags.containsKey("name")) {
				objStream.println("o " + object.getClass().getSimpleName() + " " + osmElement.tags.getValue("name"));
			} else if (osmElement != null && osmElement.tags.containsKey("ref")) {
				objStream.println("o " + object.getClass().getSimpleName() + " " + osmElement.tags.getValue("ref"));
			} else {
				objStream.println("o " + object.getClass().getSimpleName() + anonymousWOCounter++);
			}

		}

	}

	@Override
	public void drawTriangles(Material material,
			Collection<? extends TriangleXYZ> triangles,
			List<List<VectorXZ>> texCoordLists) {

		int i = 0;

		List<List<VectorXZ>> subLists = new ArrayList<List<VectorXZ>>();

		for (TriangleXYZ triangle : triangles) {

			subLists.clear();
			for (List<VectorXZ> list : texCoordLists) {
				subLists.add(list.subList(3 * i, 3 * (i + 1)));
			}

			drawTriangle(material, triangle, null, subLists);

			i++;
		}
	}

	private final static double NORMAL_PRECISION = 10000;
	private final static double COORD_PRECISION = 1000;

	public void drawTriangle(Material material, TriangleXYZ triangle,
			List<VectorXYZ> normals, List<List<VectorXZ>> texCoordLists) {

		List<VectorXYZ> vs = triangle.getVertices();
		VectorXYZ faceNormal = triangle.getNormal();
		VectorXYZ clampedNormal = new VectorXYZ(
				Math.round(faceNormal.x * NORMAL_PRECISION) / NORMAL_PRECISION,
				Math.round(faceNormal.y * NORMAL_PRECISION) / NORMAL_PRECISION,
				Math.round(faceNormal.z * NORMAL_PRECISION) / NORMAL_PRECISION);

		int[] n = normalsToIndices(ImmutableList.of(clampedNormal));
		int[] normalIndices = { n[0], n[0], n[0] };
		int[] vertexIndices = verticesToIndices(vs);

		for (int layer = 0; layer < max(1, material.getNumTextureLayers()); layer++) {

			useMaterial(material, layer);

			int[] texCoordIndices = null;
			if (texCoordLists != null && !texCoordLists.isEmpty()) {
				texCoordIndices = texCoordsToIndices(texCoordLists.get(layer));
			}
			if (layer > 0)
				vertexIndices = verticesToIndices(offsetVertices(vs, nCopies(vs.size(), faceNormal),
						layer * SMALL_OFFSET));

			writeFace(vertexIndices, normalIndices, texCoordIndices);
		}
	}

	@Override
	public void drawFace(Material material, List<VectorXYZ> vs,
			List<VectorXYZ> normals, List<List<VectorXZ>> texCoordLists) {

		int[] normalIndices = null;
		// if (vs.get(0) instanceof TriangleXYZWithNormals)
		if (normals != null) {
			normalIndices = normalsToIndices(vs);
		}

		VectorXYZ faceNormal = new TriangleXYZ(vs.get(0), vs.get(1), vs.get(2)).getNormal();

		for (int layer = 0; layer < max(1, material.getNumTextureLayers()); layer++) {

			useMaterial(material, layer);

			int[] texCoordIndices = null;
			if (texCoordLists != null && !texCoordLists.isEmpty()) {
				texCoordIndices = texCoordsToIndices(texCoordLists.get(layer));
			}

			int[] vertexIndices = verticesToIndices((layer == 0) ? vs :
					offsetVertices(vs, nCopies(vs.size(), faceNormal), layer
							* SMALL_OFFSET));

			writeFace(vertexIndices, normalIndices, texCoordIndices);
		}
	}

	@Override
	public void drawTrianglesWithNormals(Material material,
			Collection<? extends TriangleXYZWithNormals> triangles,
			List<List<VectorXZ>> texCoordLists) {

		for (int layer = 0; layer < max(1, material.getNumTextureLayers()); layer++) {

			useMaterial(material, layer);

			int triangleNumber = 0;
			for (TriangleXYZWithNormals t : triangles) {

				int[] texCoordIndices = null;
				if (texCoordLists != null && !texCoordLists.isEmpty()) {
					List<VectorXZ> texCoords = texCoordLists.get(layer);
					texCoordIndices = texCoordsToIndices(
							texCoords.subList(3 * triangleNumber, 3 * triangleNumber + 3));
				}

				writeFace(
						verticesToIndices((layer == 0) ? t.getVertices() : offsetVertices(t.getVertices(),
								t.getNormals(), layer * SMALL_OFFSET)),
						normalsToIndices(t.getNormals()), texCoordIndices);

				triangleNumber++;
			}

		}

	}

	private void useMaterial(Material material, int layer) {
		if (!material.equals(currentMaterial) || (layer != currentMaterialLayer)) {

			String name = materialMap.get(material);
			if (name == null) {
				name = Materials.getUniqueName(material);
				if (name == null) {
					name = "MAT_" + anonymousMaterialCounter;
					anonymousMaterialCounter += 1;
				}
				materialMap.put(material, name);
				writeMaterial(material, name);
			}

			objStream.println("usemtl " + name + "_" + layer);

			currentMaterial = material;
			currentMaterialLayer = layer;
		}
	}

	private List<? extends VectorXYZ> offsetVertices(List<? extends VectorXYZ> vs, List<VectorXYZ> directions,
			double offset) {

		List<VectorXYZ> result = new ArrayList<VectorXYZ>(vs.size());

		for (int i = 0; i < vs.size(); i++) {
			result.add(vs.get(i).add(directions.get(i).mult(offset)));
		}

		return result;
	}

	private int[] verticesToIndices(List<? extends VectorXYZ> vs) {
		return vectorsToIndices(vertexIndexMap, "v ", vs);
	}

	private int[] normalsToIndices(List<? extends VectorXYZ> normals) {
		return vectorsToIndices(normalsIndexMap, "vn ", normals);
	}

	private int[] texCoordsToIndices(List<VectorXZ> texCoords) {
		return vectorsToIndices(texCoordsIndexMap, "vt ", texCoords);
	}

	private <V> int[] vectorsToIndices(Map<V, Integer> indexMap,
			String objLineStart, List<? extends V> vectors) {

		int[] indices = new int[vectors.size()];

		for (int i = 0; i < vectors.size(); i++) {
			final V v = vectors.get(i);
			Integer index = indexMap.get(v);
			if (index == null) {
				index = indexMap.size();
				objStream.println(objLineStart + formatVector(v));
				indexMap.put(v, index);
			}
			indices[i] = index;
		}

		return indices;

	}

	private final StringBuilder sb = new StringBuilder();

	private String formatVector(Object v) {

		if (v instanceof VectorXYZ) {
			VectorXYZ vXYZ = (VectorXYZ) v;
			sb.setLength(0);
			sb.append(Math.round(vXYZ.x * COORD_PRECISION) / COORD_PRECISION);
			sb.append(' ');
			sb.append(Math.round(vXYZ.y * COORD_PRECISION) / COORD_PRECISION);
			sb.append(' ');
			sb.append(-Math.round(vXYZ.z * COORD_PRECISION) / COORD_PRECISION);
			return sb.toString();
		} else {
			VectorXZ vXZ = (VectorXZ) v;
			sb.setLength(0);
			sb.append(Math.round(vXZ.x * COORD_PRECISION) / COORD_PRECISION);
			sb.append(' ');
			sb.append(Math.round(vXZ.z * COORD_PRECISION) / COORD_PRECISION);
			return sb.toString();
		}
	}

	private String formatVectorScaled(Object v) {

		if (v instanceof VectorXYZ) {
			VectorXYZ vXYZ = (VectorXYZ) v;
			sb.setLength(0);
			sb.append(vXYZ.x);
			sb.append(' ');
			sb.append(vXYZ.y);
			sb.append(' ');
			sb.append(-vXYZ.z);
			return sb.toString();
		} else {
			VectorXZ vXZ = (VectorXZ) v;
			sb.setLength(0);
			sb.append(vXZ.x);
			sb.append(' ');
			sb.append(vXZ.z);
			return sb.toString();
		}

	}

	private void writeFace(int[] vertexIndices, int[] normalIndices,
			int[] texCoordIndices) {

		assert normalIndices == null
				|| vertexIndices.length == normalIndices.length;

		objStream.print("f");

		for (int i = 0; i < vertexIndices.length; i++) {
			sb.setLength(0);
			sb.append(' ');
			sb.append(vertexIndices[i] + 1);

			sb.append('/');
			if (texCoordIndices != null)
				sb.append(texCoordIndices[i] + 1);
			sb.append('/');

			if (normalIndices != null)
				sb.append(normalIndices[i] + 1);

			objStream.print(sb.toString());
		}

		objStream.println();
	}

	private void writeMaterial(Material material, String name) {

		for (int i = 0; i < max(1, material.getNumTextureLayers()); i++) {

			TextureData textureData = null;
			if (material.getTextureDataList().size() > 0) {
				textureData = material.getTextureDataList().get(i);
			}

			mtlStream.println("newmtl " + name + "_" + i);

			if (textureData == null || textureData.colorable) {
				writeColorLine("Ka", material.ambientColor());
				writeColorLine("Kd", material.diffuseColor());
				// Ks
				// Ns
			} else {
				writeColorLine("Ka", multiplyColor(WHITE, material.getAmbientFactor()));
				writeColorLine("Kd", multiplyColor(WHITE, 1 - material.getAmbientFactor()));
				// Ks
				// Ns
			}

			if (textureData != null) {
				mtlStream.println("map_Ka " + textureData.file);
				mtlStream.println("map_Kd " + textureData.file);
			}
			mtlStream.println();
		}
	}

	private void writeColorLine(String lineStart, Color color) {

		mtlStream.println(lineStart
				+ " " + color.getRed() / 255f
				+ " " + color.getGreen() / 255f
				+ " " + color.getBlue() / 255f);

	}

}
