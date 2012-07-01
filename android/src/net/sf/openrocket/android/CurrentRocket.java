package net.sf.openrocket.android;

import java.io.File;
import java.io.IOException;

import net.sf.openrocket.aerodynamics.WarningSet;
import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.document.Simulation;
import net.sf.openrocket.document.StorageOptions;
import net.sf.openrocket.file.openrocket.OpenRocketSaver;
import net.sf.openrocket.rocketcomponent.Rocket;
import android.net.Uri;

public class CurrentRocket {

	private Uri fileUri;

	private OpenRocketDocument rocketDocument;
	private WarningSet warnings;

	private RocketChangedEventHandler handler;
	
	private boolean isModified = false;
	
	public void setHandler( RocketChangedEventHandler handler ) {
		this.handler = handler;
	}
	
	/**
	 * @return the rocketDocument
	 */
	public OpenRocketDocument getRocketDocument() {
		return rocketDocument;
	}

	public void notifySimsChanged() {
		isModified = true;
		if ( handler != null ) {
			handler.simsChangedMessage();
		}
	}

	public void addNewSimulation() {
		Rocket rocket = rocketDocument.getRocket();
		// FIXME - hopefully the change to the Simulation object will be reverted soon.
		Simulation newSim = new Simulation(rocketDocument, rocket);
		newSim.setName(rocketDocument.getNextSimulationName());
		rocketDocument.addSimulation(newSim);
		notifySimsChanged();
	}
	
	public void deleteSimulation( int simulationPos ) {
		rocketDocument.removeSimulation( simulationPos );
		notifySimsChanged();
	}
	
	public String addNewMotorConfig() {
		isModified = true;
		String configId = rocketDocument.getRocket().newMotorConfigurationID();
		if ( handler != null ) {
			handler.configsChangedMessage();
		}
		return configId;
	}
	/**
	 * @param rocketDocument the rocketDocument to set
	 */
	public void setRocketDocument(OpenRocketDocument rocketDocument) {
		this.rocketDocument = rocketDocument;
		isModified = false;
	}

	public WarningSet getWarnings() {
		return warnings;
	}

	public void setWarnings(WarningSet warnings) {
		this.warnings = warnings;
	}

	public Uri getFileUri() {
		return fileUri;
	}

	public void setFileUri(Uri fileUri) {
		this.fileUri = fileUri;
	}

	public boolean isModified() {
		return this.isModified;
	}
	
	public void saveOpenRocketDocument() throws IOException {
		OpenRocketSaver saver = new OpenRocketSaver();
		StorageOptions options = new StorageOptions();
		options.setCompressionEnabled(true);
		options.setSimulationTimeSkip(StorageOptions.SIMULATION_DATA_ALL);
		saver.save(new File(fileUri.getPath()),rocketDocument,options);
		isModified = false;
	}

}
