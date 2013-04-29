package org.osm2world.console;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.osm2world.core.ConversionFacade;
import org.osm2world.core.ConversionFacade.Phase;
import org.osm2world.core.ConversionFacade.ProgressListener;
import org.osm2world.core.ConversionFacade.Results;
import org.osm2world.core.map_elevation.creation.ZeroElevationCalculator;
import org.osm2world.core.osm.creation.OverpassAPIReader;
import org.osm2world.core.target.common.rendering.OrthoTilesUtil;
import org.osm2world.core.target.common.rendering.TileNumber;
import org.osm2world.core.target.gltf.BinWriter;
import org.osm2world.core.target.obj.ObjWriter;
import org.osm2world.core.world.creation.WorldModule;
import org.osm2world.core.world.modules.BuildingModule;

public class TileGenerator {
	public static void main(String[] args) {

		long start = System.currentTimeMillis();

		ConversionFacade cf = new ConversionFacade();
		PerformanceListener perfListener = new PerformanceListener();

		cf.addProgressListener(perfListener);

		cf.setElevationCalculator(new ZeroElevationCalculator());

		String query = "(way[\"building\"]{{bbox}};"
				+ "way[\"building:part\"]{{bbox}};"
				+ "way[\"roof:ridge\"]{{bbox}};"
				+ "way[\"roof:edge\"]{{bbox}};" + ")->.parts;"
				+ "relation(bw.parts)[\"type\"~\"multipolygon\"]->.poly;"
				+ "relation(bw.parts)[\"type\"~\"building\"]->.rel;"
				+ "(.parts;way(r.poly);)->.parts;"
				+ "(node(w.parts);node(r.rel);.parts;.rel;);out;";

		int tileX = 34371;
		int tileY = 21325;
		int tileZ = 16;

//		int tileX = 34371 >> 1;
//		int tileY = 21325 >> 1;
//		int tileZ = 15;

		double top = OrthoTilesUtil.tile2lat(tileY, tileZ);
		double bottom = OrthoTilesUtil.tile2lat(tileY + 1, tileZ);
		double left = OrthoTilesUtil.tile2lon(tileX, tileZ);
		double right = OrthoTilesUtil.tile2lon(tileX + 1, tileZ);

		// double bottom = 53.071107696397085;
		// double left = 8.806142807006836;
		// double top = 53.07795294848583;
		// double right = 8.817451000213623;

		// OverpassAPISource source = new OverpassAPISource(left, right, top,
		// bottom, null, query);

		OverpassAPIReader source = new OverpassAPIReader(left, right, top,
				bottom, null, query);

		Configuration config = new BaseConfiguration();
		config.setProperty("createTerrain", Boolean.FALSE);
		config.setProperty("renderUnderground", Boolean.FALSE);
		config.setProperty("MapCenterLon", left);
		config.setProperty("MapCenterLat", bottom);

		try {
			List<WorldModule> modules = Arrays
					.asList((WorldModule) new BuildingModule());

			Results results = cf.createRepresentations(source, modules, config,
					null);

			results.getMapProjection();

			long startWrite = System.currentTimeMillis();
			
			BinWriter.writeObjFile(new File("test.obj"), results.getMapData(),
					results.getEleData(), results.getTerrain(),
					results.getMapProjection(), null, null);
					
//			ObjWriter.writeObjFile(new File("test.obj"), results.getMapData(),
//					results.getEleData(), results.getTerrain(),
//					results.getMapProjection(), null, null);

			System.out.println("write took "
					+ (System.currentTimeMillis() - startWrite));
		} catch (IOException e) {
			e.printStackTrace();
		}

		String a = String
				.format("|MAP_DATA %6d |REPRESENTATION %6d |ELEVATION %6d |TERRAIN %6d |%6d |%6d |\n",
						(perfListener.getPhaseDuration(Phase.MAP_DATA) + 500) / 1000,
						(perfListener.getPhaseDuration(Phase.REPRESENTATION) + 500) / 1000,
						(perfListener.getPhaseDuration(Phase.ELEVATION) + 500) / 1000,
						(perfListener.getPhaseDuration(Phase.TERRAIN) + 500) / 1000,
						(System.currentTimeMillis()
								- perfListener.getPhaseEnd(Phase.TERRAIN) + 500) / 1000,
						(System.currentTimeMillis() - start + 500) / 1000);

		System.out.println(a);
	}

	private static class PerformanceListener implements ProgressListener {

		private Phase currentPhase = null;
		private long currentPhaseStart;

		private Map<Phase, Long> phaseStarts = new HashMap<Phase, Long>();
		private Map<Phase, Long> phaseEnds = new HashMap<Phase, Long>();

		public Long getPhaseStart(Phase phase) {
			return phaseStarts.get(phase);
		}

		public Long getPhaseEnd(Phase phase) {
			return phaseEnds.get(phase);
		}

		public Long getPhaseDuration(Phase phase) {
			return getPhaseEnd(phase) - getPhaseStart(phase);
		}

		@Override
		public void updatePhase(Phase newPhase) {

			phaseStarts.put(newPhase, System.currentTimeMillis());

			if (currentPhase != null) {

				phaseEnds.put(currentPhase, System.currentTimeMillis());

				long ms = System.currentTimeMillis() - currentPhaseStart;
				System.out.println("phase " + currentPhase + " finished after "
						+ ms + " ms");

			}

			currentPhase = newPhase;
			currentPhaseStart = System.currentTimeMillis();

		}
	}
}
