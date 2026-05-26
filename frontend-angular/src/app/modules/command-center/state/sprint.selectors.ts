import { createFeatureSelector, createSelector } from '@ngrx/store';
import { SprintState } from './sprint.reducers';

export const selectSprintState = createFeatureSelector<SprintState>('sprint');

export const selectSprintConfidence = createSelector(
  selectSprintState,
  (state: SprintState) => state.confidenceScore
);

export const selectVelocityTrend = createSelector(
  selectSprintState,
  (state: SprintState) => state.velocityTrend
);

export const selectBlockerCount = createSelector(
  selectSprintState,
  (state: SprintState) => state.blockerCount
);

export const selectActiveRisks = createSelector(
  selectSprintState,
  (state: SprintState) => state.activeRisks
);

export const selectSprintBurndown = createSelector(
  selectSprintState,
  (state: SprintState) => {
    // Generate static guidelines for visual representation
    return {
      ideal: [40, 35, 30, 25, 20, 15, 10, 5, 0],
      actual: [40, 38, 33, 29, 24],
      predicted: [24, 19, 14, 9, 3, 0]
    };
  }
);
