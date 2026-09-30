package com.sparsh.sentinel.scoring.model;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The three exported models, loaded once at start-up.
 *
 * <p>The {@code .onnx} files are build output of {@code ml/}, not source, so a fresh clone does
 * not have them. Without them the service still starts and scores with the rules alone - it says
 * so loudly at start-up rather than failing, because the rules are what regulation requires and
 * must not depend on a training run.
 */
@Component
@EnableConfigurationProperties(ModelProperties.class)
public class Models implements AutoCloseable {

    static final String CLASSIFIER = "fraud_xgboost.onnx";
    static final String ANOMALY = "anomaly_isoforest.onnx";
    static final String SEGMENTS = "segments_kmeans.onnx";

    private static final Logger log = LoggerFactory.getLogger(Models.class);

    private final Loaded loaded;

    public Models(ModelProperties properties) {
        this.loaded = load(Path.of(properties.dir()).toAbsolutePath().normalize());
    }

    /**
     * @param anomalyThresholds per peer segment: an Isolation Forest score below the segment's
     *                          threshold is among that segment's most unusual transactions.
     */
    public record Loaded(OnnxModel classifier, OnnxModel anomaly, OnnxModel segments,
                         Map<Long, Float> anomalyThresholds) {
    }

    private static Loaded load(Path dir) {
        Path classifier = dir.resolve(CLASSIFIER);
        Path anomaly = dir.resolve(ANOMALY);
        Path segments = dir.resolve(SEGMENTS);
        if (!Files.exists(classifier) || !Files.exists(anomaly) || !Files.exists(segments)) {
            log.warn("No models in {} - scoring with the rules only. Run the notebooks in ml/notebooks "
                    + "to export {}, {} and {}.", dir, CLASSIFIER, ANOMALY, SEGMENTS);
            return null;
        }

        OnnxModel segmentModel = OnnxModel.load(segments);
        Map<Long, Float> thresholds = segmentModel.metadata("anomaly_thresholds",
                        new TypeReference<Map<String, Float>>() { })
                .entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> Long.valueOf(e.getKey()), Map.Entry::getValue));

        Loaded loaded = new Loaded(OnnxModel.load(classifier), OnnxModel.load(anomaly), segmentModel, thresholds);
        log.info("Loaded models from {}: {}, {}, {} ({} peer segments)",
                dir, CLASSIFIER, ANOMALY, SEGMENTS, thresholds.size());
        return loaded;
    }

    public Optional<Loaded> loaded() {
        return Optional.ofNullable(loaded);
    }

    @Override
    public void close() throws OrtException {
        if (loaded != null) {
            loaded.classifier().close();
            loaded.anomaly().close();
            loaded.segments().close();
        }
    }
}
