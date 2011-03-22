package org.osm2world.core.world.creation;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.osm2world.core.map_data.data.MapData;

public class WorldCreator {

	private Collection<WorldModule> modules;
		
	public WorldCreator(Configuration configuration, WorldModule... modules) {
		this.modules = Arrays.asList(modules);
	}
	
	public WorldCreator(Configuration config, List<WorldModule> modules) {
		this.modules = modules;
		for (WorldModule module : modules) {
			module.setConfiguration(config);
		}
	}
	
	public void addRepresentationsTo(MapData grid) {
		
		for (WorldModule module : modules) {
			module.applyTo(grid);
		}
		
		NetworkCalculator.calculateNetworkInformationInGrid(grid);
		
	}
	
}
