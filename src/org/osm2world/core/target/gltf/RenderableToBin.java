package org.osm2world.core.target.gltf;

import org.osm2world.core.target.common.RenderableToPrimitiveTarget;

public interface RenderableToBin extends RenderableToPrimitiveTarget {

	public void renderTo(BinTarget target);

}
