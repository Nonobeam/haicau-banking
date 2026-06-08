package per.nonobeam.saga;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import per.nonobeam.common.channel.ChannelRequest;
import per.nonobeam.common.channel.ChannelResponse;
import per.nonobeam.deadletter.DeadLetterWriter;
import per.nonobeam.health.OrchestratorFailureTracker;
import per.nonobeam.health.ParticipantHealthMap;
import per.nonobeam.internal.channel.HttpCommunicationChannel;
import per.nonobeam.internal.saga.SagaStepRequest;
import per.nonobeam.internal.saga.SagaStepResponse;
import per.nonobeam.saga.event.SagaNotificationPublisher;

@Component
public class SagaOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

  private final SagaRepository sagaRepository;
  private final SagaRuleEngine ruleEngine;
  private final SagaStepResolver stepResolver;
  private final ParticipantHealthMap healthMap;
  private final DeadLetterWriter deadLetterWriter;
  private final CircuitComposer circuitComposer;
  private final HttpCommunicationChannel channel;
  private final OrchestratorFailureTracker failureTracker;
  private final SagaNotificationPublisher notificationPublisher;

  public SagaOrchestrator(
      SagaRepository sagaRepository,
      SagaRuleEngine ruleEngine,
      SagaStepResolver stepResolver,
      ParticipantHealthMap healthMap,
      DeadLetterWriter deadLetterWriter,
      CircuitComposer circuitComposer,
      HttpCommunicationChannel channel,
      OrchestratorFailureTracker failureTracker,
      SagaNotificationPublisher notificationPublisher) {
    this.sagaRepository = sagaRepository;
    this.ruleEngine = ruleEngine;
    this.stepResolver = stepResolver;
    this.healthMap = healthMap;
    this.deadLetterWriter = deadLetterWriter;
    this.circuitComposer = circuitComposer;
    this.channel = channel;
    this.failureTracker = failureTracker;
    this.notificationPublisher = notificationPublisher;
  }

  public void advance(SagaInstance saga) {
    try {
      SagaStep step = stepResolver.resolve(saga.getSagaType(), saga.getCurrentState());

      if (!circuitComposer.isCircuitClosed(step.participantId(), step.operationType())) {
        park(saga, step.participantId());
        return;
      }

      saga.setCurrentState("IN_PROGRESS");
      transition(saga, "IN_PROGRESS");

      StepResult result = dispatch(step, saga);

      if (result.isSuccess()) {
        transition(saga, result.nextState());
      } else {
        handleFailure(saga, result.failureType());
      }
    } catch (SagaVersionConflictException e) {
      log.warn("Version conflict advancing saga {}, skipping", saga.getSagaId());
    } catch (Exception e) {
      handleFailure(saga, "INTERNAL_ERROR");
    }
  }

  private StepResult dispatch(SagaStep step, SagaInstance saga) {
    try {
      var request = new SagaStepRequest(saga.getSagaId(), step.type(), saga.getPayload());
      String destination = step.participantId() + "/internal/v1/saga/step";
      ChannelResponse<SagaStepResponse> response =
          channel.pull(
              destination, ChannelRequest.of(request, saga.getSagaId()), SagaStepResponse.class);
      SagaStepResponse resp = response.body();
      if (resp == null) {
        failureTracker.recordFailure(step.participantId());
        return StepResult.retriableFailure("EMPTY_RESPONSE");
      }
      return switch (resp.outcome()) {
        case "SUCCESS" -> StepResult.success(resp.nextState());
        case "RETRIABLE_FAILURE" -> {
          failureTracker.recordFailure(step.participantId());
          yield StepResult.retriableFailure(resp.failureType());
        }
        default -> {
          failureTracker.recordFailure(step.participantId());
          yield StepResult.failure(resp.failureType());
        }
      };
    } catch (Exception e) {
      failureTracker.recordFailure(step.participantId());
      log.warn("Dispatch to {} failed: {}", step.participantId(), e.getMessage());
      return StepResult.retriableFailure("PARTICIPANT_TIMEOUT");
    }
  }

  private void handleFailure(SagaInstance saga, String failureType) {
    Decision decision =
        ruleEngine.decide(
            saga.getSagaType(),
            failureType,
            saga.getRetryCount() != null ? saga.getRetryCount() : 0);
    switch (decision.type()) {
      case "RETRY" -> scheduleRetry(saga, decision.delayMs());
      case "PARK" -> park(saga, failureType);
      case "UNDO" -> beginUndo(saga);
      default -> deadLetter(saga, failureType);
    }
  }

  private static final java.util.Set<String> TERMINAL_SUCCESS_STATES =
      java.util.Set.of("COMMITTED", "COMPENSATED");

  private void transition(SagaInstance saga, String nextState) {
    int updated =
        sagaRepository.compareAndSetState(
            saga.getSagaId(), saga.getCurrentState(), nextState, saga.getVersion());
    if (updated == 0) {
      throw new SagaVersionConflictException(saga.getSagaId());
    }
    saga.setCurrentState(nextState);
    if (TERMINAL_SUCCESS_STATES.contains(nextState)) {
      notificationPublisher.publish(saga, nextState);
    }
  }

  private void scheduleRetry(SagaInstance saga, int delayMs) {
    saga.setCurrentState("RETRYING");
    saga.setRetryEligibleAfter(OffsetDateTime.now().plusNanos((long) delayMs * 1_000_000));
    saga.setRetryCount(saga.getRetryCount() != null ? saga.getRetryCount() + 1 : 1);
    sagaRepository.save(saga);
  }

  private void park(SagaInstance saga, String participantId) {
    saga.setCurrentState("PARKED");
    saga.setParkedOnParticipant(participantId);
    sagaRepository.save(saga);
  }

  private void beginUndo(SagaInstance saga) {
    saga.setCurrentState("UNDOING");
    sagaRepository.save(saga);
  }

  private void deadLetter(SagaInstance saga, String failureReason) {
    saga.setCurrentState("DEAD_LETTERED");
    sagaRepository.save(saga);
    deadLetterWriter.write(saga, failureReason);
    notificationPublisher.publish(saga, "DEAD_LETTERED");
  }
}
