/*
 * Copyright 2015 Sven Meier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package svenmeier.coxswain;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import java.util.ArrayList;
import java.util.List;

import propoid.core.Propoid;
import propoid.db.LookupException;
import propoid.db.Match;
import propoid.db.Order;
import propoid.db.Reference;
import propoid.db.Repository;
import propoid.db.Transaction;
import propoid.db.cascading.DefaultCascading;
import svenmeier.coxswain.gym.Difficulty;
import svenmeier.coxswain.gym.Measurement;
import svenmeier.coxswain.gym.Program;
import svenmeier.coxswain.gym.Segment;
import svenmeier.coxswain.gym.Snapshot;
import svenmeier.coxswain.gym.Workout;

import static propoid.db.Where.all;
import static propoid.db.Where.equal;
import static propoid.db.Where.greaterEqual;
import static propoid.db.Where.lessThan;

public class Gym {

    private static Gym instance;

    private Context context;

    private Repository repository;

    private List<Listener> listeners = new ArrayList<>();

    /**
     * The selected program.
     */
    public Program program;

	/**
     * Optional pace workout.
     */
    public Workout pace;

	/**
     * The current workout.
     */
    public Workout current;

    /**
     * The last measurement.
     */
    public Measurement measurement = new Measurement();

	/**
     * Progress of current workout.
     */
    public Progress progress;

    private Gym(Context context) {

        this.context = context;

        repository = new Repository(context, "gym");

        ((DefaultCascading) repository.cascading).setCascaded(new Program().segments);

        Workout workoutIndex = new Workout();
        repository.index(workoutIndex, false, Order.descending(workoutIndex.start));
        Snapshot snapshotIndex = new Snapshot();
        repository.index(snapshotIndex, false, Order.ascending(snapshotIndex.workout));
    }

    public void defaults() {
        Match<Program> query = repository.query(new Program());
        if (query.count() > 0) {
            return;
        }

        repository.insert(Program.meters(String.format(context.getString(R.string.distance_meters), 500), 500, Difficulty.EASY));
        repository.insert(Program.meters(String.format(context.getString(R.string.distance_meters), 1000), 1000, Difficulty.EASY));
        repository.insert(Program.meters(String.format(context.getString(R.string.distance_meters), 2000), 2000, Difficulty.MEDIUM));

        repository.insert(Program.calories(String.format(context.getString(R.string.energy_calories), 200), 200, Difficulty.MEDIUM));

        repository.insert(Program.minutes(String.format(context.getString(R.string.duration_minutes), 5), 5, Difficulty.EASY));
        repository.insert(Program.minutes(String.format(context.getString(R.string.duration_minutes), 10), 10, Difficulty.MEDIUM));

        repository.insert(Program.strokes(String.format(context.getString(R.string.strokes_count), 500), 500, Difficulty.MEDIUM));

        Program program = new Program(context.getString(R.string.program_name_segments));
        program.getSegment(0).setDistance(1000);
        program.addSegment(new Segment(Difficulty.HARD).setDuration(60).setStrokeRate(30));
        program.addSegment(new Segment(Difficulty.EASY).setDistance(1000));
        program.addSegment(new Segment(Difficulty.HARD).setDuration(60).setStrokeRate(30));
        program.addSegment(new Segment(Difficulty.EASY).setDistance(1000));
        program.addSegment(new Segment(Difficulty.HARD).setDuration(60).setStrokeRate(30));
        program.addSegment(new Segment(Difficulty.EASY).setDistance(1000));
        program.addSegment(new Segment(Difficulty.HARD).setDuration(60).setStrokeRate(30));
        program.addSegment(new Segment(Difficulty.EASY).setDistance(1000));
        repository.insert(program);
    }

    public boolean hasListener(Class<?> clazz) {
        for (Listener listener : listeners) {
            if (clazz.isInstance(listener)) {
                return true;
            }
        }
        return false;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public Match<Program> getPrograms() {
        return repository.query(new Program());
    }

    public Program getProgram(Reference<Program> reference) {
        return repository.lookup(reference);
    }

    public <P extends Propoid> P get(Reference<P> reference) {
        return repository.lookup(reference);
    }

    public void add(final String programName, final Workout workout, final List<Snapshot> snapshots) {

        repository.transactional(new Transaction() {
            @Override
            public void doTransactional() {
                Program example = new Program();

                // imported workouts are not evaluated by default
                workout.evaluate.set(false);

                workout.program.set(repository.query(example, equal(example.name, programName)).first());
                repository.merge(workout);

                for (Snapshot snapshot : snapshots) {
                    snapshot.workout.set(workout);

                    repository.merge(snapshot);
                }
            }
        });
    }

    public void mergeProgram(Program program) {
        repository.merge(program);
    }

    public Match<Workout> getWorkouts() {
        return repository.query(new Workout());
    }

    public Match<Workout> getWorkouts(long from, long to) {
        Workout propotype = new Workout();

        // evaluated workouts only
        return repository.query(propotype, all(
                equal(propotype.evaluate, true),
                greaterEqual(propotype.start, from),
                lessThan(propotype.start, to))
        );
    }

    public void delete(Propoid propoid) {
        if (propoid instanceof Workout) {
            Snapshot prototype = new Snapshot();
            repository.query(prototype, equal(prototype.workout, (Workout) propoid)).delete();
        }

        repository.delete(propoid);
    }

    public void mergeWorkout(Workout workout) {
        repository.merge(workout);
    }

    public void deselect() {
        this.pace = null;
        this.program = null;

        this.measurement = new Measurement();
        this.current = null;
        this.progress = null;

        fireChanged();
    }

    public void repeat(Program program) {
        this.pace = null;
        this.program = program;

        this.measurement = new Measurement();
        this.current = null;
        this.progress = null;

        fireChanged();
    }

    public void repeat(Workout pace) {
        Program program = null;
        try {
            program = pace.program.get();
        } catch (LookupException programAlreadyDeleted) {
            // fall back to challenge
            challenge(pace);
            return;
        }

        this.pace = pace;
        this.program = program;

        this.measurement = new Measurement();
        this.current = null;
        this.progress = null;

        fireChanged();
    }

    public void challenge(Workout pace) {
        this.pace = pace;
        this.program = Program.meters(context.getString(R.string.action_challenge), pace.distance.get(), Difficulty.NONE);

        this.measurement = new Measurement();
        this.current = null;
        this.progress = null;

        fireChanged();
    }

	/**
     * A new measurement.
     *
     * @param measurement
     */
    public Event onMeasured(Measurement measurement) {
        Event event = Event.ACKNOLEDGED;

        this.measurement = measurement;

        if (program != null) {
            // program is selected

            if (measurement.distance > 0 || measurement.duration > 0) {
                // delay workout creation

                if (current == null) {
                    current = program.newWorkout();
                    current.location.set(getLocation());

                    progress = new Progress(program.getSegment(0), new Measurement());

                    event = Event.PROGRAM_START;
                }

                if (current.onMeasured(measurement)) {
                    mergeWorkout(current);

                    Snapshot snapshot = new Snapshot(measurement);
                    snapshot.workout.set(current);
                    repository.insert(snapshot);
                }

                if (progress != null && progress.completion() == 1.0f) {
                    Segment next = program.getNextSegment(progress.segment);
                    if (next == null) {
                        mergeWorkout(current);

                        progress = null;

                        event = Event.PROGRAM_FINISHED;
                    } else {
                        progress = new Progress(next, measurement);

                        event = Event.SEGMENT_CHANGED;
                    }
                }
            }
        }

        fireChanged();

        return event;
    }

    public Match<Snapshot> getSnapshots(Workout workout) {
        Snapshot prototype = new Snapshot();

        return repository.query(prototype, equal(prototype.workout, workout));
    }

    public Location getLocation() {
        Location bestLocation = null;

        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location == null) {
                    continue;
                }

                if (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()) {
                    bestLocation = location;
                }
            }
        } catch (SecurityException ex) {
        }

        return bestLocation;
    }

    public class Progress {

        public final Segment segment;

        /**
         * Measurement of start of segment
         */
        private final Measurement startMeasurement;

        public Progress(Segment segment, Measurement measurement) {
            this.segment = segment;

            this.startMeasurement = new Measurement(measurement);
        }

        public float completion() {
            float achieved = achieved();
            float target = segment.getTarget();

            return Math.min(achieved / target, 1.0f);
        }

        public int achieved() {
            return achieved(measurement) - achieved(startMeasurement);
        }

        private int achieved(Measurement measurement) {
            if (segment.distance.get() > 0) {
                return measurement.distance;
            } else if (segment.strokes.get() > 0) {
                return measurement.strokes;
            } else if (segment.energy.get() > 0) {
                return measurement.energy;
            } else if (segment.duration.get() > 0){
                return measurement.duration;
            }
            return 0;
        }

        public boolean inLimit() {
            if (measurement.speed < progress.segment.speed.get()) {
                return false;
            } else if (measurement.pulse < progress.segment.pulse.get()) {
                return false;
            } else if (measurement.strokeRate < progress.segment.strokeRate.get()) {
                return false;
            }

            return true;
        }

        public String describeTarget() {
            String target = "";

            if (segment.distance.get() > 0) {
                target = String.format(context.getString(R.string.distance_meters), segment.distance.get());
            } else if (segment.strokes.get() > 0) {
                target = String.format(context.getString(R.string.strokes_count), segment.strokes.get());
            } else if (segment.energy.get() > 0) {
                target = String.format(context.getString(R.string.energy_calories), segment.energy.get());
            } else if (segment.duration.get() > 0) {
                target = String.format(context.getString(R.string.duration_minutes), Math.round(segment.duration.get() / 60f));
            }
            return target;
        }

        public String describeLimit() {
            String limit = "";

            if (segment.strokeRate.get() > 0) {
                limit = String.format(context.getString(R.string.strokeRate_strokesPerMinute), segment.strokeRate.get());
            } else if (segment.speed.get() > 0) {
                limit = String.format(context.getString(R.string.speed_metersPerSecond), segment.speed.get() / 100f);
            } else if (segment.pulse.get() > 0){
                 limit = String.format(context.getString(R.string.pulse_beatsPerMinute), segment.pulse.get());
            }

            return limit;
        }

        public String describe() {
            StringBuilder description = new StringBuilder();

            description.append(describeTarget());

            String limit = describeLimit();
            if (limit.isEmpty() == false) {
                description.append(", ");
                description.append(limit);
            }

            return description.toString();
        }
    }

    private void fireChanged() {
        for (Listener listener : listeners) {
            listener.changed();
        }
    }

    public static Gym instance(Context context) {
        if (instance == null) {
            instance = new Gym(context.getApplicationContext());
        }

        return instance;
    }

    public interface Listener {
        void changed();
    }
}
