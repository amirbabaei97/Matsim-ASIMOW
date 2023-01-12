package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.network.NetworkUtils;
import org.matsim.api.core.v01.Id;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;

public class EmissionEventHandler implements BasicEventHandler {

    private final Scenario scenario;
    private final Network network;
    Map<Tuple<Integer, Integer>, Map<Integer, Map<String, Double>>> grids = new HashMap<>();
    Map<String, Tuple<Integer, Integer>> linkToGrid = new HashMap<>();
    // emission_elements = ['CO', 'NOx', 'PM', 'CO2_TOTAL']
    List<String> emission_elements = List.of("CO", "NOx", "PM", "CO2_TOTAL");
    Map<String,Integer> agent_last_act = new HashMap<>();

    // constructor
    public EmissionEventHandler(Scenario scenario) {
        this.scenario = scenario;
        this.network = scenario.getNetwork();
    }

    @Override
    public void handleEvent(Event event) {
        // Check if the event is a cold or warm emission event
        if (event.getEventType().contains("Emission")) {
            Map<String, String> attributes = event.getAttributes();
            // Get the link ID from the event
            String linkId = attributes.get("linkId");
            Tuple<Integer, Integer> gridCell;
            if (!linkToGrid.containsKey(linkId)) {
                // Get the link from the network
                Link link = network.getLinks().get(Id.create(linkId, Link.class));
                // Get the link's coordinates
                Node fromNode = link.getFromNode();
                Node toNode = link.getToNode();
                double x = (fromNode.getCoord().getX() + toNode.getCoord().getX())/2;
                double y = (fromNode.getCoord().getY() + toNode.getCoord().getY())/2;
                Integer xGrid = (int) Math.floor(x / 10);
                Integer yGrid = (int) Math.floor(y / 10);
                gridCell = new Tuple<>(xGrid, yGrid);
                linkToGrid.put(linkId, gridCell);
                if (!grids.containsKey(gridCell)) {
                    Map<Integer, Map<String, Double>> timeStep = new HashMap<>();
                    grids.put(gridCell, timeStep);
                }
            } else {
                gridCell = linkToGrid.get(linkId);
            }
            //TODO: If you want to add the spread of the elements, change me.
            // Get the time step from the event
            Integer timeStep = (int) Math.floor(event.getTime() / 3600);
            if (!grids.get(gridCell).containsKey(timeStep)) {
                Map<String, Double> emissions = new HashMap<>();
                for (String emissionElement : emission_elements) {
                    emissions.put(emissionElement, 0.0);
                }
                emissions.put("population", 0.0);
                grids.get(gridCell).put(timeStep, emissions);
            }
            for (String emissionElement : emission_elements) {
                if (attributes.containsKey(emissionElement)) {
                    Double emission = Double.parseDouble(attributes.get(emissionElement));
                    Double currentEmission = grids.get(gridCell).get(timeStep).get(emissionElement);
                    grids.get(gridCell).get(timeStep).put(emissionElement, currentEmission + emission);
                }
            }
        }
        else if (event.getEventType().contains("actend")) {
            Map<String, String> attributes = event.getAttributes();
            String agentId = attributes.get("person");
            Integer lastAct = agent_last_act.getOrDefault(agentId, -1);
            Integer timeStep = (int) Math.floor(event.getTime() / 3600);
            double x = Double.parseDouble(attributes.get("x"));
            double y = Double.parseDouble(attributes.get("y"));
            Integer xGrid = (int) Math.floor(x / 10);
            Integer yGrid = (int) Math.floor(y / 10);
            Tuple<Integer, Integer> gridCell = new Tuple<>(xGrid, yGrid);
            for (int i = lastAct + 1; i <= timeStep; i++) {
                if (!grids.containsKey(gridCell)) {
                    Map<Integer, Map<String, Double>> timeStepMap = new HashMap<>();
                    grids.put(gridCell, timeStepMap);
                }
                if (!grids.get(gridCell).containsKey(i)) {
                    Map<String, Double> emissions = new HashMap<>();
                    for (String emissionElement : emission_elements) {
                        emissions.put(emissionElement, 0.0);
                    }
                    emissions.put("population", 1.0);
                }
                else {
                    Double currentPopulation = grids.get(gridCell).get(i).get("population");
                    grids.get(gridCell).get(i).put("population", currentPopulation + 1.0);
                }
            }
            agent_last_act.put(agentId, timeStep);
        }
    }

    @Override
    public void reset(int iteration) {
        grids.clear();
        linkToGrid.clear();
    }

    public void writeToJson(String path) {
        JSONObject json = new JSONObject();
        for (Tuple<Integer, Integer> gridCell : grids.keySet()) {
            JSONObject gridCellJson = new JSONObject();
            for (Integer timeStep : grids.get(gridCell).keySet()) {
                JSONObject timeStepJson = new JSONObject();
                for (String emissionElement : emission_elements) {
                    // round to 4 decimal places
                    timeStepJson.put(emissionElement, Math.round(grids.get(gridCell).get(timeStep).get(emissionElement) * 10000.0) / 10000.0);
                }
                timeStepJson.put("population", Math.round(grids.get(gridCell).get(timeStep).get("population") * 10000.0) / 10000.0);
                gridCellJson.put(timeStep.toString(), timeStepJson);
            }
            String gridCellString = gridCell.getFirst().toString() + "," + gridCell.getSecond().toString();
            json.put(gridCellString, gridCellJson);
        }
        // beautify json
        String jsonString = json.toString(4);
        try (FileWriter file = new FileWriter(path)) {
            file.write(jsonString);
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Example output:
        // {
        //     "0,0": {
        //         "0": {
        //             "CO": 0.0,
        //             "NOx": 0.0,
        //             ...
        //             "population": 1.0
        //         },
    }
}
