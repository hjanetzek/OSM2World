package org.osm2world.core.target.gltf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

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

import darwin.jopenctm.compression.MG1Encoder;
import darwin.jopenctm.io.CtmFileWriter;

/**
 * utility class for creating an Wavefront OBJ file
 */
public final class BinWriter {

	/** prevents instantiation */
	private BinWriter() {
	}

	public static final void writeObjFile(File objFile, MapData mapData,
			CellularTerrainElevation eleData, Terrain terrain,
			MapProjection mapProjection, Camera camera, Projection projection)
			throws IOException {

		if (!objFile.exists()) {
			objFile.createNewFile();
		}

		File mtlFile = new File(objFile.getAbsoluteFile() + ".mtl");
		if (!mtlFile.exists()) {
			mtlFile.createNewFile();
		}

		
		File ctmFile = new File(objFile.getAbsoluteFile() + ".ctm");
		if (ctmFile.exists()) {
			ctmFile.delete();
		}
		
		
		//FileOutputStream idxStream = new FileOutputStream(ctmFile);
		CtmFileWriter ctmWriter = new CtmFileWriter(new FileOutputStream(ctmFile), new MG1Encoder(), 9);

		//		File vtxFile = new File(objFile.getAbsoluteFile() + ".vert");
//		if (!vtxFile.exists()) {
//			vtxFile.createNewFile();
//		}
//
//		File idxFile = new File(objFile.getAbsoluteFile() + ".idx");
//		if (!idxFile.exists()) {
//			idxFile.createNewFile();
//		}

		PrintStream objStream = new PrintStream(objFile);
		PrintStream mtlStream = new PrintStream(mtlFile);

//		FileOutputStream vtxStream = new FileOutputStream(vtxFile);

		/* write comments at the beginning of both files */

		writeObjHeader(objStream, mapProjection);

		writeMtlHeader(mtlStream);

		/* write path of mtl file to obj file */

		objStream.println("mtllib " + mtlFile.getName() + "\n");

		/* write actual file content */

		BinTarget target = new BinTarget(objStream, mtlStream, ctmWriter);

		TargetUtil.renderWorldObjects(target, mapData, true);

		if (terrain != null) {
			TargetUtil.renderObject(target, terrain);
		}

		target.finish();

		objStream.close();
		mtlStream.close();

	}

	private static final void writeObjHeader(PrintStream objStream,
			MapProjection mapProjection) {

		objStream.println("# This file was created by OSM2World "
				+ GlobalValues.VERSION_STRING + " - "
				+ GlobalValues.OSM2WORLD_URI + "\n");
		objStream.println("# Projection information:");
		objStream.println("# Coordinate origin (0,0,0): " + "lat "
				+ mapProjection.calcLat(VectorXZ.NULL_VECTOR) + ", " + "lon "
				+ mapProjection.calcLon(VectorXZ.NULL_VECTOR) + ", " + "ele 0");
		objStream.println("# North direction: "
				+ new VectorXYZ(mapProjection.getNorthUnit().x, 0,
						-mapProjection.getNorthUnit().z));
		objStream.println("# 1 coordinate unit corresponds to roughly "
				+ "1 m in reality\n");

	}

	private static final void writeMtlHeader(PrintStream mtlStream) {

		mtlStream.println("# This file was created by OSM2World "
				+ GlobalValues.VERSION_STRING + " - "
				+ GlobalValues.OSM2WORLD_URI + "\n");

	}

}
