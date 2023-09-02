package org.matsim.project;

import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.ActivityEndEvent;
// import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
// import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.vehicles.Vehicle;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;

import org.matsim.api.core.v01.population.Person;

import java.util.regex.Pattern;

import java.util.function.DoubleFunction;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Define a custom handler for LinkEnterEvent.
class AgentTrackerEventHandler
        implements LinkEnterEventHandler, LinkLeaveEventHandler, PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler, ActivityStartEventHandler, ActivityEndEventHandler,
        PersonDepartureEventHandler, PersonArrivalEventHandler {

    private final Network network;
    private final Pattern[] ignorePatterns;

    // vehicle to list of persons map
    private final Map<Id<Vehicle>, List<Id<Person>>> vehicleToPersonMap = new HashMap<>();

    // Link to LinkCoords map
    private final Map<Id<Link>, LinkCoords> linkCoordsMap = new HashMap<>();

    // Person to list of PersonEvent map
    public final Map<Id<Person>, List<PersonEvent>> personToEventMap = new HashMap<>();

    public AgentTrackerEventHandler(Network network, Pattern[] ignorePatterns) {
        this.network = network;
        this.ignorePatterns = ignorePatterns;
    }

    class LinkCoords {
        public Coord from;
        public Coord to;
        public Coord mid;

        public LinkCoords(Coord from, Coord to) {
            this.from = from;
            this.to = to;
            this.mid = new Coord((from.getX() + to.getX()) / 2, (from.getY() + to.getY()) / 2);
        }
    }

    class PersonEvent {
        public String type;
        public Id<Vehicle> vehicleId;
        public Id<Link> linkId;
        public Coord coord;
        public double time;
        public String xFunction;
        public String yFunction;

        public PersonEvent(String type, Id<Vehicle> vehicleId, Id<Link> linkId, Coord coord,
                double time, String xFunction, String yFunction) {
            this.type = type;
            this.vehicleId = vehicleId;
            this.linkId = linkId;
            this.coord = coord;
            this.time = time;
            this.xFunction = xFunction;
            this.yFunction = yFunction;
        }
    }

    private boolean shouldIgnorePerson(String personId) {
        for (Pattern pattern : ignorePatterns) {
            if (pattern.matcher(personId).matches()) {
                return true;
            }
        }
        return false;
    }

    public static DoubleFunction<Coord> getPositionFunction(Coord fromCoord, Coord toCoord, double t) {
        return (double tPrime) -> {
            if (tPrime < 0 || tPrime > t) {
                throw new IllegalArgumentException("t' must be in the range [0, t]");
            }

            double progress = tPrime / t;

            double newX = fromCoord.getX() + progress * (toCoord.getX() - fromCoord.getX());
            double newY = fromCoord.getY() + progress * (toCoord.getY() - fromCoord.getY());

            return new Coord(newX, newY);
        };
    }

    public static String getPositionEquation(Coord fromCoord, Coord toCoord, double t) {
        StringBuilder equation = new StringBuilder();
        equation.append("x = ").append((int) fromCoord.getX()).append(" + (t / ").append(t).append(") * (")
                .append((int) (toCoord.getX() - fromCoord.getX())).append(")\n");
        equation.append("y = ").append((int) fromCoord.getY()).append(" + (t / ").append(t).append(") * (")
                .append((int) (toCoord.getY() - fromCoord.getY())).append(")\n");
        return equation.toString();
    }

    public LinkCoords getLinkCoords(Id<Link> linkId) {
        if (!linkCoordsMap.containsKey(linkId)) {
            Link link = network.getLinks().get(linkId);
            linkCoordsMap.put(linkId, new LinkCoords(link.getFromNode().getCoord(), link.getToNode().getCoord()));
        }
        return linkCoordsMap.get(linkId);
    }

    @Override
    public void reset(int iteration) {
        System.out.println("Resetting...");
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        // check if vehicleToPersonMap contains vehicleId and it is not empty
        if (!vehicleToPersonMap.containsKey(vehicleId) || vehicleToPersonMap.get(vehicleId).isEmpty()) {
            return;
        }
        List<Id<Person>> personIds = vehicleToPersonMap.get(vehicleId);
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).from;
        double time = event.getTime();
        for (Id<Person> personId : personIds) {
            PersonEvent personEvent = new PersonEvent(event.getEventType(), vehicleId, linkId, coord, time, "", "");
            personToEventMap.get(personId).add(personEvent);
        }
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        // check if vehicleToPersonMap contains vehicleId and it is not empty
        if (!vehicleToPersonMap.containsKey(vehicleId) || vehicleToPersonMap.get(vehicleId).isEmpty()) {
            return;
        }
        List<Id<Person>> personIds = vehicleToPersonMap.get(vehicleId);
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).to;
        double time = event.getTime();
        for (Id<Person> personId : personIds) {
            PersonEvent lastPersonEvent = personToEventMap.get(personId)
                    .get(personToEventMap.get(personId).size() - 1);
            // if type is not LinkEnterEvent, then ignore
            if (!lastPersonEvent.type.equals("entered link")) {
                continue;
            }
            String xyFunction = getPositionEquation(lastPersonEvent.coord, coord, time - lastPersonEvent.time);
            String xFunction = xyFunction.split("\n")[0];
            String yFunction = xyFunction.split("\n")[1];
            lastPersonEvent.xFunction = xFunction;
            lastPersonEvent.yFunction = yFunction;
        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Vehicle> vehicleId = event.getVehicleId();
        Id<Person> personId = event.getPersonId();
        if (!vehicleToPersonMap.containsKey(vehicleId)) {
            vehicleToPersonMap.put(vehicleId, new ArrayList<>());
        }
        vehicleToPersonMap.get(vehicleId).add(personId);
        if (!personToEventMap.containsKey(personId)) {
            personToEventMap.put(personId, new ArrayList<>());
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Vehicle> vehicleId = event.getVehicleId();
        Id<Person> personId = event.getPersonId();
        if (!vehicleToPersonMap.containsKey(vehicleId)) {
            return;
        }
        vehicleToPersonMap.get(vehicleId).remove(personId);
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        return;
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Person> personId = event.getPersonId();
        double lastTime = 0;
        if (!personToEventMap.containsKey(personId)) {
            personToEventMap.put(personId, new ArrayList<>());
        } else {
            lastTime = personToEventMap.get(personId).get(personToEventMap.get(personId).size() - 1).time;
        }
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).mid;
        double time = event.getTime();
        String xFunction = new StringBuilder().append((int) coord.getX()).append(" + (t * 0)").toString();
        String yFunction = new StringBuilder().append((int) coord.getY()).append(" + (t * 0)").toString();
        PersonEvent personEvent = new PersonEvent(event.getActType(), null, linkId, coord, time, xFunction,
                yFunction);
        personToEventMap.get(personId).add(personEvent);
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if (!event.getLegMode().equals("walk")) {
            return;
        }
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).mid;
        double time = event.getTime();
        PersonEvent personEvent = new PersonEvent(event.getEventType(), null, linkId, coord, time, "", "");
        personToEventMap.get(event.getPersonId()).add(personEvent);
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if (!event.getLegMode().equals("walk")) {
            return;
        }
        if (shouldIgnorePerson(event.getPersonId().toString())) {
            return;
        }
        PersonEvent lastPersonEvent = personToEventMap.get(event.getPersonId())
                .get(personToEventMap.get(event.getPersonId()).size() - 1);
        // if type is not PersonDepartureEvent, then ignore
        if (!lastPersonEvent.type.equals("departure")) {
            return;
        }
        Id<Link> linkId = event.getLinkId();
        Coord coord = getLinkCoords(linkId).mid;
        double time = event.getTime();
        String xyFunction = getPositionEquation(lastPersonEvent.coord, coord, time - lastPersonEvent.time);
        String xFunction = xyFunction.split("\n")[0];
        String yFunction = xyFunction.split("\n")[1];
        lastPersonEvent.xFunction = xFunction;
        lastPersonEvent.yFunction = yFunction;
    }

    public void saveToCSV(String filePath) {
        try (FileWriter csvWriter = new FileWriter(filePath)) {
            csvWriter.write("personId,type,vehicleId,linkId,coord,time,xFunction,yFunction\n");
            for (Map.Entry<Id<Person>, List<PersonEvent>> entry : personToEventMap.entrySet()) {
                Id<Person> personId = entry.getKey();
                for (PersonEvent personEvent : entry.getValue()) {
                    csvWriter.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                            personId, personEvent.type,
                            personEvent.vehicleId == null ? "" : personEvent.vehicleId,
                            personEvent.linkId, personEvent.coord, personEvent.time,
                            personEvent.xFunction, personEvent.yFunction));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}