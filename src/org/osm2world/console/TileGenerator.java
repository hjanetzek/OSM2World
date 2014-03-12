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
import org.osm2world.core.map_data.creation.OriginMapProjection;
import org.osm2world.core.map_data.creation.TileProjection;
import org.osm2world.core.osm.creation.OverpassAPIReader;
import org.osm2world.core.target.obj.ObjWriter;
import org.osm2world.core.util.functions.Factory;
import org.osm2world.core.world.creation.WorldModule;
import org.osm2world.core.world.modules.BuildingModule;

import com.vividsolutions.jts.geom.Envelope;

public class TileGenerator {

	static Envelope tileToBBox(long x, long y, int zoom) {
		double scale = 1 << zoom;

		return new Envelope(TileProjection.xToLon(x / scale),
				TileProjection.xToLon((x + 1) / scale),
				TileProjection.yToLat(y / scale),
				TileProjection.yToLat((y + 1) / scale));

	}

	public static void main(String[] args) {

		long start = System.currentTimeMillis();

		ConversionFacade cf = new ConversionFacade();
		PerformanceListener perfListener = new PerformanceListener();

		cf.addProgressListener(perfListener);

		String query = "(way[\"building\"]{{bbox}};"
				+ "way[\"building:part\"]{{bbox}};"
				+ "way[\"roof:ridge\"]{{bbox}};"
				+ "way[\"roof:edge\"]{{bbox}};" + ")->.parts;"
				+ "relation(bw.parts)[\"type\"~\"multipolygon\"]->.poly;"
				+ "relation(bw.parts)[\"type\"~\"building\"]->.rel;"
				+ "(.parts;way(r.poly);)->.parts;"
				+ "(node(w.parts);node(r.rel);.parts;.rel;);out;";

		int x = 17185;
		int y = 10662;
		int z = 15;

		if (args.length == 3) {
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
			z = Integer.parseInt(args[2]);
		}

		final Envelope bbox = tileToBBox(x, y, z);
		System.out.println(bbox);

		cf.setMapProjectionFactory(new Factory<OriginMapProjection>() {
			@Override
			public OriginMapProjection make() {
				return new TileProjection(bbox);
			}
		});

		OverpassAPIReader source = new OverpassAPIReader(
				bbox.getMinX(), bbox.getMaxX(),
				bbox.getMaxY(), bbox.getMinY(),
				null, query);

		Configuration config = new BaseConfiguration();
		config.setProperty("createTerrain", Boolean.FALSE);
		config.setProperty("renderUnderground", Boolean.FALSE);
		// config.setProperty("MapCenterLon", left);
		// config.setProperty("MapCenterLat", bottom);

		try {
			List<WorldModule> modules = Arrays
					.asList((WorldModule) new BuildingModule());

			Results results = cf.createRepresentations(source, modules, config,
					null);

			results.getMapProjection();

			long startWrite = System.currentTimeMillis();
			ObjWriter.writeObjFile(new File("test.obj"), results.getMapData(),
					results.getMapProjection(), null, null);

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
