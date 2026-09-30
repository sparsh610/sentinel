package com.sparsh.sentinel.scoring.model;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Replays the rows each notebook recorded at export time through ONNX Runtime in Java and
 * expects Python's answers back. Catches a converter or runtime difference that would otherwise
 * show up as quietly different scores.
 *
 * <p>Skipped where the models have not been exported - the {@code .onnx} files are build output
 * of {@code ml/}, not committed, so CI does not have them.
 */
class OnnxParityTest {

    @Test
    void classifierGivesPythonsProbabilities() throws Exception {
        try (OnnxModel model = load(Models.CLASSIFIER)) {
            Parity parity = Parity.of(model);
            for (int i = 0; i < parity.rows(); i++) {
                float p = model.floats(parity.input(i), "probabilities")[1];
                assertThat((double) p).isCloseTo(parity.expected("probability", i), within(1e-5));
            }
        }
    }

    @Test
    void isolationForestGivesPythonsScores() throws Exception {
        try (OnnxModel model = load(Models.ANOMALY)) {
            Parity parity = Parity.of(model);
            for (int i = 0; i < parity.rows(); i++) {
                float score = model.floats(parity.input(i), "scores")[0];
                assertThat((double) score).isCloseTo(parity.expected("score", i), within(1e-5));
            }
        }
    }

    @Test
    void segmentModelPutsRowsInPythonsSegments() throws Exception {
        try (OnnxModel model = load(Models.SEGMENTS)) {
            Parity parity = Parity.of(model);
            for (int i = 0; i < parity.rows(); i++) {
                assertThat(model.label(parity.input(i), "label")).isEqualTo((long) parity.expected("segment", i));
            }
        }
    }

    private static OnnxModel load(String file) {
        Path path = ModelFeaturesTest.MODELS.resolve(file);
        assumeTrue(Files.exists(path), file + " not exported - run the notebooks in ml/notebooks");
        return OnnxModel.load(path);
    }

    /** {@code {"inputs": [[...], ...], "<output>": [...]}}, as the notebooks wrote it. */
    private record Parity(Map<String, List<Object>> raw) {

        static Parity of(OnnxModel model) {
            return new Parity(model.metadata("parity", new TypeReference<>() { }));
        }

        int rows() {
            return raw.get("inputs").size();
        }

        float[] input(int row) {
            List<?> values = (List<?>) raw.get("inputs").get(row);
            float[] x = new float[values.size()];
            for (int i = 0; i < x.length; i++) {
                x[i] = ((Number) values.get(i)).floatValue();
            }
            return x;
        }

        double expected(String output, int row) {
            return ((Number) raw.get(output).get(row)).doubleValue();
        }
    }
}
