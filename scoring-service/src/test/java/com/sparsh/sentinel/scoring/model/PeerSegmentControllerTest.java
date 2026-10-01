package com.sparsh.sentinel.scoring.model;

import com.sparsh.sentinel.scoring.score.ScoredTransaction;
import com.sparsh.sentinel.scoring.score.ScoredTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.sparsh.sentinel.scoring.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeerSegmentControllerTest {

    @Mock private ScoredTransactionRepository ledger;
    @Mock private FeatureBuilder features;
    @Mock private Models models;
    @Mock private OnnxModel anomaly;
    @Mock private OnnxModel segments;
    @Mock private OnnxModel classifier;

    private final float[] x = new float[ModelFeatures.NAMES.size()];

    private PeerSegmentController controller() {
        return new PeerSegmentController(ledger, features, models);
    }

    @Test
    void placesTheTransactionInItsSegmentAndJudgesItAgainstThatSegmentsThreshold() {
        ScoredTransaction scored = ScoredTransaction.of(event("DEBIT", "TRANSFER", "8000", "DE"), Instant.now());
        when(models.loaded()).thenReturn(Optional.of(
                new Models.Loaded(classifier, anomaly, segments, Map.of(1L, -0.11f))));
        when(ledger.findById(scored.getTransactionId())).thenReturn(Optional.of(scored));
        when(features.build(any())).thenReturn(x);
        when(segments.label(eq(x), eq("label"))).thenReturn(1L);
        when(anomaly.floats(eq(x), eq("scores"))).thenReturn(new float[] {-0.05f});

        PeerSegmentController.PeerSegmentView view = controller().peerSegment(scored.getTransactionId());

        assertThat(view.segment()).isEqualTo(1L);
        assertThat(view.threshold()).isEqualTo(-0.11f, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(view.unusual()).isFalse();
    }

    @Test
    void saysSoWhenThereAreNoModels() {
        when(models.loaded()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().peerSegment(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void anUnscoredTransactionIsNotFound() {
        when(models.loaded()).thenReturn(Optional.of(
                new Models.Loaded(classifier, anomaly, segments, Map.of())));
        when(ledger.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().peerSegment(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
