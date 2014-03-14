package org.osm2world.core.target.gltf;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;

import org.osm2world.core.GlobalValues;
import org.osm2world.core.heightmap.data.CellularTerrainElevation;
import org.osm2world.core.map_data.creation.MapProjection;
import org.osm2world.core.map_data.data.MapData;
import org.osm2world.core.math.VectorXYZ;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.target.TargetUtil;
import org.osm2world.core.target.common.rendering.Camera;
import org.osm2world.core.target.common.rendering.Projection;
import org.osm2world.core.terrain.data.Terrain;

/**
 * utility class for creating an Wavefront OBJ file
 */
public final class BinWriter {

	/** prevents instantiation */
	private BinWriter() { }

	public static final void writeObjFile(
			File objFile, MapData mapData,
			CellularTerrainElevation eleData, Terrain terrain,
			MapProjection mapProjection,
			Camera camera, Projection projection)
			throws IOException {

		if (!objFile.exists()) {
			objFile.createNewFile();
		}

		File mtlFile = new File(objFile.getAbsoluteFile() + ".mtl");
		if (!mtlFile.exists()) {
			mtlFile.createNewFile();
		}

		File vtxFile = new File(objFile.getAbsoluteFile() + ".vert");
		if (!vtxFile.exists()) {
			vtxFile.createNewFile();
		}

		File idxFile = new File(objFile.getAbsoluteFile() + ".idx");
		if (!idxFile.exists()) {
			idxFile.createNewFile();
		}

		PrintStream objStream = new PrintStream(objFile);
		PrintStream mtlStream = new PrintStream(mtlFile);

		FileOutputStream idxStream = new FileOutputStream(idxFile);
		FileOutputStream vtxStream = new FileOutputStream(vtxFile);

		/* write comments at the beginning of both files */

		writeObjHeader(objStream, mapProjection);

		writeMtlHeader(mtlStream);

		/* write path of mtl file to obj file */

		objStream.println("mtllib " + mtlFile.getName() + "\n");

		/* write actual file content */

		BinTarget target = new BinTarget(objStream, mtlStream, idxStream, vtxStream);

		TargetUtil.renderWorldObjects(target, mapData, true);

		if (terrain != null) {
			TargetUtil.renderObject(target, terrain);
		}

		objStream.close();
		mtlStream.close();

	}

	public static final void writeObjFiles(
			final File objDirectory, MapData mapData,
			CellularTerrainElevation eleData, Terrain terrain,
			final MapProjection mapProjection,
			Camera camera, Projection projection,
			int primitiveThresholdPerFile)
			throws IOException {

		if (!objDirectory.exists()) {
			objDirectory.mkdir();
		}

		checkArgument(objDirectory.isDirectory());

		final File mtlFile = new File(objDirectory.getPath()
				+ File.separator + "materials.mtl");
		if (!mtlFile.exists()) {
			mtlFile.createNewFile();
		}

		final PrintStream mtlStream = new PrintStream(mtlFile);

		writeMtlHeader(mtlStream);

		/* create iterator which creates and wraps .obj files as needed */

		Iterator<BinTarget> objIterator = new Iterator<BinTarget>() {

			private int fileCounter = 0;
			PrintStream objStream = null;

			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public BinTarget next() {

				try {

					if (objStream != null) {
						objStream.close();
						fileCounter ++;
					}

					File objFile = new File(objDirectory.getPath() + File.separator
							+ "part" + format("%04d", fileCounter) + ".obj");

					if (!objFile.exists()) {
						objFile.createNewFile();
					}

					objStream = new PrintStream(objFile);

					writeObjHeader(objStream, mapProjection);

					objStream.println("mtllib " + mtlFile.getName() + "\n");

					return null; //new BinTarget(objStream, mtlStream, );

				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		};

		/* write file content */

		TargetUtil.renderWorldObjects(objIterator, mapData, primitiveThresholdPerFile);
		TargetUtil.renderObject(objIterator.next(), terrain);

		mtlStream.close();

	}

	private static final void writeObjHeader(PrintStream objStream,
			MapProjection mapProjection) {

		objStream.println("# This file was created by OSM2World "
				+ GlobalValues.VERSION_STRING + " - "
				+ GlobalValues.OSM2WORLD_URI + "\n");
		objStream.println("# Projection information:");
		objStream.println("# Coordinate origin (0,0,0): "
				+ "lat " + mapProjection.calcLat(VectorXZ.NULL_VECTOR) + ", "
				+ "lon " + mapProjection.calcLon(VectorXZ.NULL_VECTOR) + ", "
				+ "ele 0");
		objStream.println("# North direction: " + new VectorXYZ(
						mapProjection.getNorthUnit().x, 0,
						- mapProjection.getNorthUnit().z));
		objStream.println("# 1 coordinate unit corresponds to roughly "
				+ "1 m in reality\n");

	}

	private static final void writeMtlHeader(PrintStream mtlStream) {

		mtlStream.println("# This file was created by OSM2World "
				+ GlobalValues.VERSION_STRING + " - "
				+ GlobalValues.OSM2WORLD_URI + "\n");

	}

}
