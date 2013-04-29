package org.osm2world.core.target.gltf;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osm2world.core.map_data.data.MapArea;
import org.osm2world.core.map_data.data.MapElement;
import org.osm2world.core.map_data.data.MapNode;
import org.osm2world.core.map_data.data.MapWaySegment;
import org.osm2world.core.math.TriangleXYZWithNormals;
import org.osm2world.core.math.VectorXYZ;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.osm.data.OSMElement;
import org.osm2world.core.target.common.FaceTarget;
import org.osm2world.core.target.common.TextureData;
import org.osm2world.core.target.common.material.Material;
import org.osm2world.core.target.common.material.Materials;
import org.osm2world.core.world.data.WorldObject;

import darwin.jopenctm.data.AttributeData;
import darwin.jopenctm.data.Mesh;
import darwin.jopenctm.errorhandling.InvalidDataException;
import darwin.jopenctm.io.CtmFileWriter;

public class BinTarget extends FaceTarget<RenderableToBin> {

	private final PrintStream objStream;
	private final PrintStream mtlStream;
	private final CtmFileWriter ctmWriter;
	
	private final Map<VectorXYZ, Integer> vertexIndexMap = new HashMap<VectorXYZ, Integer>();
	private final Map<VectorXYZ, Integer> normalsIndexMap = new HashMap<VectorXYZ, Integer>();
	private final Map<VectorXZ, Integer> texCoordsIndexMap = new HashMap<VectorXZ, Integer>();
	private final Map<Material, String> materialMap = new HashMap<Material, String>();

	private final List<Integer> indicesList = new ArrayList<Integer>(1 << 15);
	private final List<Vertex> vertexList = new ArrayList<Vertex>(1 << 15);
	private final Map<Vertex, Integer> vertexMap = new HashMap<Vertex, Integer>();

	private Class<? extends WorldObject> currentWOGroup = null;
	private int anonymousWOCounter = 0;

	private Material currentMaterial = null;
	private static int anonymousMaterialCounter = 0;
	private StringBuffer buf;

	static class Vertex {
		int hash;

		VectorXYZ position;
		VectorXYZ normal;
		VectorXZ tex;

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Vertex))
				return false;

			Vertex other = (Vertex) obj;

			if (!position.equals(other.position))
				return false;

			if (normal == null) {
				if (other.normal != null)
					return false;
			} else {
				if (other.normal == null)
					return false;

				if (!normal.equals(other.normal))
					return false;
			}

			if (tex == null) {
				if (other.tex != null)
					return false;
			} else {
				if (other.tex == null)
					return false;

				if (!tex.equals(other.tex))
					return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			if (hash != 0)
				return hash;

			hash = 31 + 7 * position.hashCode();

			if (normal != null)
				hash *= 7 * normal.hashCode();

			if (tex != null)
				hash *= 7 * tex.hashCode();

			return hash;
		}
	}

	public BinTarget(PrintStream objStream, PrintStream mtlStream, CtmFileWriter ctmWriter) {
		this.objStream = objStream;
		this.mtlStream = mtlStream;
		this.ctmWriter = ctmWriter;
	}

	@Override
	public Class<RenderableToBin> getRenderableType() {
		return RenderableToBin.class;
	}

	@Override
	public void render(RenderableToBin renderable) {
		renderable.renderTo(this);
	}

	@Override
	public boolean reconstructFaces() {
		return false;
	}

	@Override
	public void beginObject(WorldObject object) {
		if (buf != null) {
			objStream.print(buf);
		}

		buf = new StringBuffer(8192);

		if (object == null) {
			currentWOGroup = null;
			buf.append("g null\no null\n");
		} else {
			/* maybe start a group depending on the object's class */
			if (!object.getClass().equals(currentWOGroup)) {
				currentWOGroup = object.getClass();
				buf.append("g ");
				buf.append(currentWOGroup.getSimpleName());
				buf.append('\n');
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

			buf.append("o ");
			buf.append(object.getClass().getSimpleName());
			buf.append(' ');

			if (osmElement != null && osmElement.tags.containsKey("name")) {
				buf.append(osmElement.tags.getValue("name"));
			} else if (osmElement != null && osmElement.tags.containsKey("ref")) {
				buf.append(osmElement.tags.getValue("ref"));
			} else {
				buf.append(anonymousWOCounter++);
			}
			buf.append('\n');
		}

	}

	@Override
	public void finish() {
		int[] indices = new int[indicesList.size()];
		for (int i = 0, n = indicesList.size(); i < n; i++)
			indices[i] = indicesList.get(i);
		
		System.out.println("indices: " + indicesList.size() + " vertices " + vertexList.size());
		
		float[] vertices = new float[vertexList.size() * 3];
		float[] normals = new float[vertexList.size() * 3];
		
		for (int i = 0, n = vertexList.size(); i < n; i++){
			Vertex v = vertexList.get(i);
			
			vertices[i*3+0] = (float)v.position.x;
			vertices[i*3+1] = (float)v.position.y;
			vertices[i*3+2] = (float)v.position.z;

//			normals[i*3+0] = (float)v.normal.x;
//			normals[i*3+1] = (float)v.normal.y;
//			normals[i*3+2] = (float)v.normal.z;
		}	
		
		Mesh m = new Mesh(vertices, null, indices, new AttributeData[0], new AttributeData[0]);
		try {
			this.ctmWriter.encode(m, "a");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidDataException e) {
			e.printStackTrace();
		}
	};

	@Override
	public void drawFace(Material material, List<VectorXYZ> vs,
			List<VectorXYZ> normals, List<List<VectorXZ>> texCoordLists) {

		for (int i = 0, n = vs.size(); i < n; i++) {
			Vertex vertex = new Vertex();
			vertex.position = vs.get(i);

//			if (normals != null)
//				vertex.normal = normals.get(i);

			// if (texCoordLists != null)
			// vertex.tex= texCoordLists.get(i);

			Integer idx = vertexMap.get(vertex);
			if (idx == null){
				idx = vertexList.size();

				vertexMap.put(vertex, idx);
				vertexList.add(vertex);
			}
			indicesList.add(idx);
		}

		// useMaterial(material);

		// int[] normalIndices = null;
		// if (normals != null) {
		// normalIndices = normalsToIndices(normals);
		// }
		//
		// int[] texCoordIndices = null;
		// if (texCoordLists != null && !texCoordLists.isEmpty()) {
		// texCoordIndices = texCoordsToIndices(texCoordLists.get(0));
		// }
		//
		// writeFace(verticesToIndices(vs), normalIndices, texCoordIndices);

	}

	@Override
	public void drawTrianglesWithNormals(Material material,
			Collection<? extends TriangleXYZWithNormals> triangles,
			List<List<VectorXZ>> texCoordLists) {

		// int triangleNumber = 0;
		for (TriangleXYZWithNormals triangle : triangles) {
			List<VectorXYZ> vs = triangle.getVertices();
			List<VectorXYZ> normals = triangle.getNormals();

			for (int i = 0, n = vs.size(); i < n; i++) {
				Vertex vertex = new Vertex();
				vertex.position = vs.get(i);

//				if (normals != null)
//					vertex.normal = normals.get(i);

				
				Integer idx = vertexMap.get(vertex);
				if (idx == null){
					vertexMap.put(vertex, vertexList.size());
					vertexList.add(vertex);
				}
			}
			// triangleNumber++;

		}

		// useMaterial(material);
		//
		// int triangleNumber = 0;
		// for (TriangleXYZWithNormals triangle : triangles) {
		//
		// int[] texCoordIndices = null;
		// if (texCoordLists != null && !texCoordLists.isEmpty()) {
		// List<VectorXZ> texCoords = texCoordLists.get(0);
		//
		// texCoordIndices = texCoordsToIndices(texCoords.subList(
		// 3 * triangleNumber, 3 * triangleNumber + 3));
		// }
		//
		// writeFace(verticesToIndices(triangle.getVertices()),
		// normalsToIndices(triangle.getNormals()), texCoordIndices);
		//
		// triangleNumber++;
		//
		// }

	}

	// private void useMaterial(Material material) {
	// if (!material.equals(currentMaterial)) {
	// currentMaterial = material;
	//
	// String name = materialMap.get(material);
	// if (name == null) {
	// name = Materials.getUniqueName(material);
	// if (name == null) {
	// name = "MAT_" + anonymousMaterialCounter;
	// anonymousMaterialCounter += 1;
	// }
	// materialMap.put(material, name);
	// writeMaterial(material, name);
	// }
	//
	// buf.append("usemat ");
	// buf.append(name);
	// buf.append('\n');
	// }
	// }
	//
	// private int[] verticesToIndices(List<? extends VectorXYZ> vs) {
	// return vectorsToIndices(vertexIndexMap, "v ", vs);
	// }
	//
	// private int[] normalsToIndices(List<? extends VectorXYZ> normals) {
	// return vectorsToIndices(normalsIndexMap, "vn ", normals);
	// }
	//
	// private int[] texCoordsToIndices(List<VectorXZ> texCoords) {
	// return vectorsToIndices(texCoordsIndexMap, "vt ", texCoords);
	// }

	// private <V> int[] vectorsToIndices(Map<V, Integer> indexMap,
	// String objLineStart, List<? extends V> vectors) {
	//
	// int[] indices = new int[vectors.size()];
	//
	// for (int i = 0; i < vectors.size(); i++) {
	// final V v = vectors.get(i);
	// Integer index = indexMap.get(v);
	// if (index == null) {
	// index = indexMap.size();
	// buf.append(objLineStart);
	// if (v instanceof VectorXYZ) {
	// VectorXYZ vXYZ = (VectorXYZ) v;
	// appendDouble(vXYZ.x, buf);
	// buf.append(' ');
	// appendDouble(vXYZ.y, buf);
	// buf.append(' ');
	// appendDouble(-vXYZ.z, buf);
	// } else {
	// VectorXZ vXZ = (VectorXZ) v;
	// appendDouble(vXZ.x, buf);
	// buf.append(' ');
	// appendDouble(-vXZ.z, buf);
	// }
	// buf.append('\n');
	// indexMap.put(v, index);
	// }
	// indices[i] = index;
	// }
	//
	// return indices;
	//
	// }
	//
	// private void writeFace(int[] vertexIndices, int[] normalIndices,
	// int[] texCoordIndices) {
	//
	// assert normalIndices == null
	// || vertexIndices.length == normalIndices.length;
	//
	// buf.append('f');
	//
	// for (int i = 0; i < vertexIndices.length; i++) {
	// buf.append(' ');
	// buf.append(vertexIndices[i] + 1);
	//
	// if (texCoordIndices != null) {
	// buf.append('/');
	// buf.append(texCoordIndices[i] + 1);
	// }
	//
	// if (normalIndices != null) {
	// if (texCoordIndices == null)
	// buf.append('/');
	//
	// buf.append('/');
	// buf.append(normalIndices[i] + 1);
	// }
	// }
	//
	// buf.append('\n');
	// }
	//
	// private void writeMaterial(Material material, String name) {
	//
	// TextureData textureData = null;
	// if (!material.getTextureDataList().isEmpty()) {
	// textureData = material.getTextureDataList().get(0);
	// }
	//
	// mtlStream.println("newmtl " + name);
	// writeColorLine("Ka", material.ambientColor());
	// writeColorLine("Kd", material.diffuseColor());
	// // Ks
	// // Ns
	// if (textureData != null) {
	// mtlStream.println("map_Ka " + textureData.file);
	// mtlStream.println("map_Kd " + textureData.file);
	// }
	// mtlStream.println();
	//
	// }

	// private void writeColorLine(String lineStart, Color color) {
	//
	// mtlStream.println(lineStart + " " + color.getRed() / 255f + " "
	// + color.getGreen() / 255f + " " + color.getBlue() / 255f);
	//
	// }

}
