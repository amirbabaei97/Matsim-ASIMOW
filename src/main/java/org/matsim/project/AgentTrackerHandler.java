/* *********************************************************************** *
 * project: org.matsim.*
 * WarmEmissionHandler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package org.matsim.project;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.Vehicle;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;

import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.FileWriter;
import java.io.IOException;

// all events types:
// array(['TransitDriverStarts', 'departure', 'PersonEntersVehicle',
//        'vehicle enters traffic', 'VehicleArrivesAtFacility',
//        'VehicleDepartsAtFacility', 'coldEmissionEvent', 'left link',
//        'entered link', 'warmEmissionEvent', 'actend', 'stuckAndAbort',
//        'vehicle leaves traffic', 'PersonLeavesVehicle', 'arrival',
//        'actstart', 'travelled', 'waitingForPt', 'vehicle aborts'],
//       dtype=object)

// all events with person is not null:
// array(['departure', 'PersonEntersVehicle', 'vehicle enters traffic',
//        'actend', 'stuckAndAbort', 'vehicle leaves traffic',
//        'PersonLeavesVehicle', 'arrival', 'actstart', 'travelled',
//        'waitingForPt'], dtype=object)

// all evnenst with vehicle is not null:
// array(['PersonEntersVehicle', 'vehicle enters traffic',
//        'VehicleArrivesAtFacility', 'VehicleDepartsAtFacility',
//        'left link', 'entered link', 'vehicle leaves traffic',
//        'PersonLeavesVehicle', 'vehicle aborts'], dtype=object)


// events needed for agent tracker:
// array(['PersonEntersVehicle', 'left link', 'entered link',
//        'actend', 'PersonLeavesVehicle', 'actstart'],


public class AgentTrackerHandler implements LinkEnterEventHandler, LinkLeaveEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityStartEventHandler, ActivityEndEventHandler {

    private static final Logger log = LogManager.getLogger(AgentTrackerHandler.class);

    // person to list of {link, {time,vehicle}}
    private final Map<Id<Person>, List<Tuple<Id<Link>, Tuple<Double, String>>>> personToLinkTimeMap = new HashMap<>();
    // vehicle to list of person
    private final Map<Id<Vehicle>, List<Id<Person>>> vehicleToPersonMap = new HashMap<>();

    // Link to coords Tuple<int, int>
    private final Map<Id<Link>, Tuple<Integer, Integer>> linkToCoordsMap = new HashMap<>();

    private final Scenario scenario;

    public AgentTrackerHandler(Scenario scenario) {
        this.scenario = scenario;
    }

    // handle LinkEnterEvent
    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        Id<Link> linkId = event.getLinkId();
        double time = event.getTime();
        String vehCat = "null";
        try{

            vehCat = scenario.getVehicles().getVehicles().get(vehicleId).getType().getId().toString();
        }
        catch (NullPointerException e){
            vehCat = scenario.getTransitVehicles().getVehicles().get(vehicleId).getType().getId().toString();
        }

        for (Id<Person> personId : vehicleToPersonMap.get(vehicleId)) {
            personToLinkTimeMap.get(personId).add(new Tuple<>(linkId, new Tuple<>(time, vehCat)));
        }
    }

    // handle LinkLeaveEvent
    @Override
    public void handleEvent(LinkLeaveEvent event) {
        return;
    }

    // handle PersonEntersVehicleEvent
    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        if (!vehicleToPersonMap.containsKey(vehicleId)) {
            vehicleToPersonMap.put(vehicleId, new ArrayList<>());
        }

        Id<Person> personId = event.getPersonId();
        // ignore if starts with pt_
        if (personId.toString().startsWith("pt_")) {
            return;
        }
        if (!personToLinkTimeMap.containsKey(personId)) {
            personToLinkTimeMap.put(personId, new ArrayList<>());
        }
        vehicleToPersonMap.get(vehicleId).add(personId);
    }

    // handle PersonLeavesVehicleEvent
    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        Id<Person> personId = event.getPersonId();
        if (personId.toString().startsWith("pt_")) {
            return;
        }
        
        Id<Vehicle> vehicleId = event.getVehicleId();
        if (vehicleToPersonMap.containsKey(vehicleId)) {
            vehicleToPersonMap.get(vehicleId).remove(personId);
        }
    }

    // handle ActivityStartEvent
    @Override
    public void handleEvent(ActivityStartEvent event) {
        Id<Person> personId = event.getPersonId();
        Id<Link> linkId = event.getLinkId();
        // Id<ActivityFacility> facilityId = event.getFacilityId();
        double time = event.getTime();
        if (!personToLinkTimeMap.containsKey(personId)) {
            personToLinkTimeMap.put(personId, new ArrayList<>());
        }
        personToLinkTimeMap.get(personId).add(new Tuple<>(linkId, new Tuple<>(time, "null")));
    }

    // handle ActivityEndEvent
    @Override
    public void handleEvent(ActivityEndEvent event) {
        return;
    }

    @Override
    public void reset(int iteration) {
        personToLinkTimeMap.clear();
        vehicleToPersonMap.clear();
    }

    public Map<Id<Person>, List<Tuple<Id<Link>, Tuple<Double, String>>>> getPersonToLinkTimeMap() {
        return personToLinkTimeMap;
    }

    public Map<Id<Vehicle>, List<Id<Person>>> getVehicleToPersonMap() {
        return vehicleToPersonMap;
    }

    // write to csv
    public void writeToFile(String fileName) {
        try {
            FileWriter writer = new FileWriter(fileName);
            writer.append("personId,linkId,time,coords,tile,vehicle");
            for (Id<Person> personId : personToLinkTimeMap.keySet()) {
                for (Tuple<Id<Link>, Tuple<Double, String>> linkTime : personToLinkTimeMap.get(personId)) {
                    Id<Link> linkId = linkTime.getFirst();
                    if (!linkToCoordsMap.containsKey(linkId)) {
                        Node fromNode = scenario.getNetwork().getLinks().get(linkId).getFromNode();
                        Node toNode = scenario.getNetwork().getLinks().get(linkId).getToNode();
                        int x = (int) (fromNode.getCoord().getX() + toNode.getCoord().getX()) / 2;
                        int y = (int) (fromNode.getCoord().getY() + toNode.getCoord().getY()) / 2;
                        linkToCoordsMap.put(linkId, new Tuple<>(x, y));
                    }
                    Tuple<Integer, Integer> coords = linkToCoordsMap.get(linkId);
                    Tuple<Integer, Integer> tile = new Tuple<>(coords.getFirst() / 10, coords.getSecond() / 10);
                    String coordsStr = coords.getFirst() + ";" + coords.getSecond();
                    String tileStr = tile.getFirst() + ";" + tile.getSecond();
                    String time = linkTime.getSecond().getFirst().toString();
                    String vehicle = linkTime.getSecond().getSecond();
                    writer.append(String.format("%n%s,%s,%s,%s,%s,%s", personId, linkId, time, coordsStr, tileStr, vehicle));
                    
                }
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }
}

