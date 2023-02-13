package org.matsim.project;

/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 * 
 */

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.VehicleUtils;


/**
 * 
 * Merges several schedule files...
 * 
 * @author ikaddoura
 *
 */
public class MergeTransitFiles {
	
	private final Logger log = Logger.getLogger(MergeTransitFiles.class);

	private final String outputDir;
	private final String networkCS ;

	private String outnetworkPrefix ;
	
	public static void main(String[] args) throws IOException {
		// String rootDirectory = "E:/projects/matsim/get_gtfs_output/";
				
		String prefix = "merged-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		// String outDir = rootDirectory;
		
		// String transitScheduleFile1 = rootDirectory + "data_u/TransitSchedule.xml.gz";
		// String transitScheduleFile2 = rootDirectory + "data_t/TransitSchedule.xml.gz";
		// String transitScheduleFile3 = rootDirectory + "data_b/TransitSchedule.xml.gz";
		// String transitScheduleFile4 = rootDirectory + "data_ice/TransitSchedule.xml.gz";
		// String transitScheduleFile5 = rootDirectory + "data_reg/TransitSchedule.xml.gz";

		// String transitVehiclesFile1 = rootDirectory + "data_u/vehicles.xml.gz";
		// String transitVehiclesFile2 = rootDirectory + "data_t/vehicles.xml.gz";
		// String transitVehiclesFile3 = rootDirectory + "data_b/vehicles.xml.gz";
		// String transitVehiclesFile4 = rootDirectory + "data_ice/vehicles.xml.gz";
		// String transitVehiclesFile5 = rootDirectory + "data_reg/vehicles.xml.gz";

		// String crs = "EPSG:3035";
		// MergeTransitFiles ptMerger = new MergeTransitFiles(crs, outDir, prefix);
		
		// Config config = ConfigUtils.createConfig();
		// config.transit().setUseTransit(true);
		// Scenario scenarioBase = ScenarioUtils.loadScenario(config);
		
		// TransitSchedule baseTransitSchedule = scenarioBase.getTransitSchedule();
		// Vehicles baseTransitVehicles = scenarioBase.getTransitVehicles();
		
		// Scenario scenario1 = ptMerger.loadScenario(transitScheduleFile1, transitVehiclesFile1);
		// Scenario scenario2 = ptMerger.loadScenario(transitScheduleFile2, transitVehiclesFile2);
		// Scenario scenario3 = ptMerger.loadScenario(transitScheduleFile3, transitVehiclesFile3);
		// Scenario scenario4 = ptMerger.loadScenario(transitScheduleFile4, transitVehiclesFile4);
		// Scenario scenario5 = ptMerger.loadScenario(transitScheduleFile5, transitVehiclesFile5);

		// MergeTransitFiles.mergeSchedule(baseTransitSchedule, "u", scenario1.getTransitSchedule());
		// MergeTransitFiles.mergeVehicles(baseTransitVehicles, "u", scenario1.getTransitVehicles());

		// MergeTransitFiles.mergeSchedule(baseTransitSchedule, "t", scenario2.getTransitSchedule());
		// MergeTransitFiles.mergeVehicles(baseTransitVehicles, "t", scenario2.getTransitVehicles());

		// MergeTransitFiles.mergeSchedule(baseTransitSchedule, "b", scenario3.getTransitSchedule());
		// MergeTransitFiles.mergeVehicles(baseTransitVehicles, "b", scenario3.getTransitVehicles());

		// MergeTransitFiles.mergeSchedule(baseTransitSchedule, "ice", scenario4.getTransitSchedule());
		// MergeTransitFiles.mergeVehicles(baseTransitVehicles, "ice", scenario4.getTransitVehicles());
        
		// MergeTransitFiles.mergeSchedule(baseTransitSchedule, "r", scenario5.getTransitSchedule());
		// MergeTransitFiles.mergeVehicles(baseTransitVehicles, "r", scenario5.getTransitVehicles());

		// ptMerger.writeFiles(baseTransitSchedule, baseTransitVehicles);

		// instead of hardcoding the files get a zip file name from args
		String zipFileName = args[0];
		// String zipFileName = "D:/Downloads/all_schedules.zip";
		String outDir;
		if (args.length > 1) {
			outDir = args[1];
		}
		else {
			outDir = "./";
		}
		String crs = "EPSG:3035";
		MergeTransitFiles ptMerger = new MergeTransitFiles(crs, outDir, prefix);
		ZipFile zipFile = new ZipFile(zipFileName);
		Enumeration<? extends ZipEntry> entries = zipFile.entries();

		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		Scenario scenarioBase = ScenarioUtils.loadScenario(config);

		TransitSchedule baseTransitSchedule = scenarioBase.getTransitSchedule();
		Vehicles baseTransitVehicles = scenarioBase.getTransitVehicles();

		// for folder in zip file
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			// if contains TransitSchedule.xml.gz
			if (entry.getName().contains("TransitSchedule.xml.gz")) {
				String folderName = entry.getName().substring(0, entry.getName().indexOf("/"));
				String vehiclesFileName = folderName + "/vehicles.xml.gz";
				ZipEntry vehiclesEntry = zipFile.getEntry(vehiclesFileName);
				
				// TransitScheduleReader tsreader = new TransitScheduleReader(scenarioBase);
				// tsreader.readStream(zipFile.getInputStream(entry));

				// MatsimVehicleReader mvreader = new MatsimVehicleReader(scenarioBase.getTransitVehicles());
				// mvreader.readStream(zipFile.getInputStream(vehiclesEntry));

				InputStream gzipStreamTransitSchedule = new GZIPInputStream(zipFile.getInputStream(entry));
				InputStream gzipStreamVehicles = new GZIPInputStream(zipFile.getInputStream(vehiclesEntry));

				Scenario tmpScenario = ptMerger.loadScenario(gzipStreamTransitSchedule, gzipStreamVehicles);
				MergeTransitFiles.mergeSchedule(baseTransitSchedule, folderName, tmpScenario.getTransitSchedule());
				MergeTransitFiles.mergeVehicles(baseTransitVehicles, folderName, tmpScenario.getTransitVehicles());
			}
		}
		zipFile.close();
		ptMerger.writeFiles(baseTransitSchedule, baseTransitVehicles);
	}
	
	private static void mergeVehicles(Vehicles baseTransitVehicles,  final String id, Vehicles transitVehicles) {
		for (VehicleType vehicleType : transitVehicles.getVehicleTypes().values()) {
			VehicleType vehicleType2 = baseTransitVehicles.getFactory().createVehicleType(Id.create(id + "_" + vehicleType.getId(), VehicleType.class));
			vehicleType2.setNetworkMode(vehicleType.getNetworkMode());
			vehicleType2.setPcuEquivalents(vehicleType.getPcuEquivalents());
			vehicleType2.setDescription(vehicleType.getDescription());
			vehicleType2.getCapacity().setSeats(vehicleType.getCapacity().getSeats());
			
			baseTransitVehicles.addVehicleType(vehicleType2);
		}
		
		for (Vehicle vehicle : transitVehicles.getVehicles().values()) {
			VehicleType v = vehicle.getType();
			VehicleType vehicleType2 = baseTransitVehicles.getFactory().createVehicleType(Id.create(id + "_" + v.getId(), VehicleType.class));
			vehicleType2.setNetworkMode(v.getNetworkMode());
			vehicleType2.setPcuEquivalents(v.getPcuEquivalents());
			vehicleType2.setDescription(v.getDescription());
			vehicleType2.getCapacity().setSeats(v.getCapacity().getSeats());

			Vehicle vehicle2 = baseTransitVehicles.getFactory().createVehicle(Id.create(id + "_" + vehicle.getId(),Vehicle.class), vehicleType2);
			baseTransitVehicles.addVehicle(vehicle2);
		}
	}

	private void writeFiles(TransitSchedule baseTransitSchedule, Vehicles transitVehicles) {
		String outSchedule = this.outputDir + outnetworkPrefix + "_transitSchedule.xml.gz";
		String outVehicles = this.outputDir + outnetworkPrefix + "_transitVehicles.xml.gz";
		log.info("Writing file to " + outSchedule);
		new TransitScheduleWriter(baseTransitSchedule).writeFile(outSchedule);
		log.info("Writing file to " + outVehicles);
		new MatsimVehicleWriter(transitVehicles).writeFile(outVehicles);
		log.info("... done.");
	}

	private Scenario loadScenario(String scheduleFile, String vehiclesFile) {
		Config config = ConfigUtils.createConfig();
		config.transit().setTransitScheduleFile(scheduleFile);
		config.transit().setVehiclesFile(vehiclesFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}
	
	private Scenario loadScenario(InputStream scheduleStream, InputStream vehiclesStream) {
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.loadScenario(config);
		TransitScheduleReader tsreader = new TransitScheduleReader(scenario);
		tsreader.readStream(scheduleStream);
		MatsimVehicleReader mvreader = new MatsimVehicleReader(scenario.getTransitVehicles());
		mvreader.readStream(vehiclesStream);
		return scenario;
	}
		
	/**
	 * Merges two networks into one, by copying all nodes and links from the addNetworks to the baseNetwork.
	 *
	 */
	public static void mergeSchedule(final TransitSchedule baseSchedule, final String id, final TransitSchedule addSchedule) {
		
		for (TransitStopFacility stop : addSchedule.getFacilities().values()) {
			Id<TransitStopFacility> newStopId = Id.create(id + "_" + stop.getId(), TransitStopFacility.class);
			TransitStopFacility stop2 = baseSchedule.getFactory().createTransitStopFacility(newStopId, stop.getCoord(), stop.getIsBlockingLane());
			stop2.setLinkId(stop.getLinkId());
			stop2.setName(stop.getName());
			baseSchedule.addStopFacility(stop2);
		}
		for (TransitLine line : addSchedule.getTransitLines().values()) {
			TransitLine line2 = baseSchedule.getFactory().createTransitLine(Id.create(id + "_" + line.getId(), TransitLine.class));
			
			for (TransitRoute route : line.getRoutes().values()) {
				
				List<TransitRouteStop> stopsWithNewIDs = new ArrayList<>();
				for (TransitRouteStop routeStop : route.getStops()) {
					Id<TransitStopFacility> newFacilityId = Id.create(id + "_" + routeStop.getStopFacility().getId(), TransitStopFacility.class);
					TransitStopFacility stop = baseSchedule.getFacilities().get(newFacilityId);
					stopsWithNewIDs.add(baseSchedule.getFactory().createTransitRouteStop(stop , routeStop.getArrivalOffset(), routeStop.getDepartureOffset()));
				}
				
				TransitRoute route2 = baseSchedule.getFactory().createTransitRoute(route.getId(), route.getRoute(), stopsWithNewIDs, route.getTransportMode());
				route2.setDescription(route.getDescription());
				
				for (Departure departure : route.getDepartures().values()) {
					Departure departure2 = baseSchedule.getFactory().createDeparture(departure.getId(), departure.getDepartureTime());
					departure2.setVehicleId(Id.create(id + "_" + departure.getVehicleId(), Vehicle.class));
					route2.addDeparture(departure2);
				}
				line2.addRoute(route2);
			}
			baseSchedule.addTransitLine(line2);
		}
	}

	public MergeTransitFiles(String networkCoordinateSystem, String outputDir, String prefix) {
		this.networkCS = networkCoordinateSystem;
		this.outputDir = outputDir.endsWith("/")?outputDir:outputDir+"/";
		this.outnetworkPrefix = prefix;
				
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDir);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("--- set the coordinate system for network to be created to " + this.networkCS + " ---");
	}
}