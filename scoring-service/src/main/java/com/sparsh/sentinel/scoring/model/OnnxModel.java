package com.sparsh.sentinel.scoring.model;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One exported model, loaded into ONNX Runtime and held for the life of the service.
 *
 * <p>The notebooks write their metadata into the {@code .onnx} file itself - the feature list,
 * and for the segment model the anomaly thresholds - so it cannot drift from the model it
 * describes. The feature list is checked here, at start-up, not on the first transaction.
 */
public class OnnxModel implements AutoCloseable {

    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INPUT = "features";

    private final String name;
    private final OrtSession session;
    private final Map<String, String> metadata;

    private OnnxModel(String name, OrtSession session, Map<String, String> metadata) {
        this.name = name;
        this.session = session;
        this.metadata = metadata;
    }

    public static OnnxModel load(Path file) {
        String name = file.getFileName().toString();
        try {
            OrtSession session = ENV.createSession(file.toString(), new OrtSession.SessionOptions());
            OnnxModel model = new OnnxModel(name, session, session.getMetadata().getCustomMetadata());
            List<String> features = model.metadata("features", new TypeReference<>() { });
            if (!ModelFeatures.NAMES.equals(features)) {
                session.close();
                throw new IllegalStateException(name + " was trained on features " + features
                        + " but this service builds " + ModelFeatures.NAMES
                        + ". Retrain the model or update ModelFeatures - never one without the other.");
            }
            return model;
        } catch (OrtException e) {
            throw new IllegalStateException("Could not load model " + file, e);
        }
    }

    public String name() {
        return name;
    }

    /** Runs the model on one feature vector and returns the named output's value. */
    public Object run(float[] features, String output) {
        try (OnnxTensor input = OnnxTensor.createTensor(ENV, new float[][] {features});
             OrtSession.Result result = session.run(Map.of(INPUT, input))) {
            return result.get(output)
                    .orElseThrow(() -> new IllegalStateException(name + " has no output " + output))
                    .getValue();
        } catch (OrtException e) {
            throw new IllegalStateException(name + " failed to score", e);
        }
    }

    /** First row of a {@code [n, k]} float output, e.g. a classifier's class probabilities. */
    public float[] floats(float[] features, String output) {
        return ((float[][]) run(features, output))[0];
    }

    /** First element of an {@code [n]} int64 output, e.g. a cluster label. */
    public long label(float[] features, String output) {
        return ((long[]) run(features, output))[0];
    }

    public <T> T metadata(String key, TypeReference<T> type) {
        String raw = metadata.get(key);
        if (raw == null) {
            throw new IllegalStateException(name + " has no '" + key + "' metadata");
        }
        try {
            return JSON.readValue(raw, type);
        } catch (IOException e) {
            throw new UncheckedIOException(name + " has unreadable '" + key + "' metadata", e);
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
