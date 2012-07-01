package net.sf.openrocket.preset.loader;

import net.sf.openrocket.material.Material;
import net.sf.openrocket.preset.ComponentPreset;
import net.sf.openrocket.preset.TypedKey;
import net.sf.openrocket.preset.TypedPropertyMap;

public class MaterialColumnParser extends BaseColumnParser {

	private final MaterialHolder materialMap;

	private final TypedKey<Material> param;
	
	public MaterialColumnParser(MaterialHolder materialMap, String columnName, TypedKey<Material> param) {
		super(columnName);
		this.param = param;
		this.materialMap = materialMap;
	}
	
	public MaterialColumnParser(MaterialHolder materialMap) {
		this(materialMap, "Material", ComponentPreset.MATERIAL);
	}
	

	@Override
	protected void doParse(String columnData, String[] data, TypedPropertyMap props) {

		if ( columnData == null || "".equals(columnData.trim())) {
			return;
		}
		
		Material.Bulk tmpMaterial = new Material.Bulk(columnData, 0.0, true);
		Material.Bulk m = materialMap.getBulkMaterial(tmpMaterial);
		props.put(param, m!= null ? m : tmpMaterial);
		
	}

}
